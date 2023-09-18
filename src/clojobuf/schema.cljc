(ns clojobuf.schema
  (:require [rubberbuf.core :refer [protoc]]
            [rubberbuf.ast-postprocess :refer [unnest]]))

(defn- qualify-name [package name]
  (keyword (if (empty? package) name (str package "/" name))))

; -------------------- msg forward -----------------------------
(defn- fxform-field [[_ rori typ name field-id options]]
  {(keyword name) [field-id typ rori options]})

(defn- fxform-map-field [[_ ktype vtype name field-id options]]
  {(keyword name) [field-id :map [ktype vtype] options]})

(defn- fxform-oneof-field [[_ typ name field-id options]]
  {(keyword name) [field-id typ :oneof options]})

(defn- fxform-oneof
  "[:oneof 'either'
           [:oneofField :string 'name' 1 nil]
           [:oneofField 'Msg' 'msg' 2 nil]]
    =>
     {:either [:oneof :name :msg],
      :name [1 :string :oneof nil],
      :msg [2 'Msg' :oneof nil]}"
  [[_ name & forms]]
  (let [oneof-fields (reduce conj (map fxform-oneof-field forms))
        oneof-select {(keyword name) (into [:oneof] (keys oneof-fields))}]
    (conj (with-meta oneof-select {:type :oneof}) oneof-fields)))

(defn- fwd-msg-child-reducer
  "msg-child is a child node within message; it can be :field, :mapField, :oneof or :option."
  [prev msg-child]
  (conj prev (condp = (first msg-child)
               :field (fxform-field msg-child)
               :mapField (fxform-map-field msg-child)
               :oneof (fxform-oneof msg-child))))

; -------------------- msg backward -----------------------------
(defn- bxform-field [[_ rori typ name field-id options]]
  {field-id [(keyword name) typ rori options]})

(defn- bxform-map-field [[_ ktype vtype name field-id options]]
  {field-id [(keyword name) :map [ktype vtype] options]})

(defn- bxform-oneof-field [oneof-name [_ typ name field-id options]]
  {field-id [(keyword name) typ [:oneof oneof-name] options]})

(defn- bxform-oneof
  "[:oneof 'either'
         [:oneofField :string 'name' 1 nil]
         [:oneofField 'Msg' 'msg' 2 nil]]
    =>
     {1 [:name :string [:oneof :either] nil],
      2 [:msg 'Msg' [:oneof :either] nil]}"
  [[_ name & forms]]
  (reduce conj (map #(bxform-oneof-field (keyword name) %) forms)))

(defn- bwd-msg-child-reducer
  "msg-child a child node within message; it can be :field, :mapField, :oneof or :option."
  [prev msg-child]
  (conj prev (condp = (first msg-child)
               :field (bxform-field msg-child)
               :mapField (bxform-map-field msg-child)
               :oneof (bxform-oneof msg-child))))

; -------------------- msg validator -----------------------------
(defn- get-checker [typ]
  (cond
    (string? typ) [:ref (keyword typ)]
    (#{:int32 :uint32 :sint32 :int64 :uint64 :sint64 :fixed32 :fixed64 :sfixed32 :sfixed64} typ) 'int?
    (= typ :string) 'string?
    (= typ :bytes) 'bytes?
    (= typ :float) 'float?
    (= typ :double) 'double?
    (= typ :bool) 'boolean?))

(defn- vxform-field [[_ rori typ name field-id options]]
  [(keyword name) (get-checker typ)])

(defn- vxform-map-field [[_ ktype vtype name field-id options]]
  [(keyword name) [:map-of [(get-checker ktype) (get-checker vtype)]]])

(defn- vxform-oneof-field [[_ typ name field-id options]]
  [(keyword name) (get-checker typ)])

(defn- vxform-oneof
  "[:oneof 'either'
       [:oneofField :string 'name' 1 nil]
       [:oneofField 'Msg' 'msg' 2 nil]]
   =>
   [:either [:enum :name :msg], :name string?, :msg [:ref :Msg]}]]"
  [[_ name & forms]]
  (reduce into
          [(keyword name) (into [:enum] (map #(keyword (nth % 2))) forms)]
          (map vxform-oneof-field forms)))

(defn- vld-msg-child-reducer
  "msg-child is child node within message; it can be :field, :mapField, :oneof or :option."
  [prev msg-child]
  (conj prev (condp = (first msg-child)
               :field (vxform-field msg-child)
               :mapField (vxform-map-field msg-child)
               :oneof (vxform-oneof msg-child))))

; -------------------- msg main -----------------------------
(defn- xform-msg [syntax package form]
  (let [fullname (qualify-name package (second form))
        ->bin (reduce fwd-msg-child-reducer {} (drop 2 form))
        <-bin (reduce bwd-msg-child-reducer {} (drop 2 form))
        validator (reduce vld-msg-child-reducer [:map {:close true}] (drop 2 form))]
    [{fullname {:syntax syntax :type :msg :encode ->bin :decode <-bin}}
     {fullname validator}]))

; ---------------------- enum -------------------------------
(defn- enum-child-reducer
  "enum-child is child node within enum; it can be :enumField or :option."
  [prev enum-child] (if (= (first enum-child) :enumField)
                      (conj prev [(keyword (second enum-child)) (nth enum-child 2)])
                      prev))

(defn- xform-enm [syntax package enm]
  (let [fullname (qualify-name package (second enm))
        tuples (reduce enum-child-reducer [] (drop 2 enm))
        default (-> tuples first first) ; for enum, the first entry is the default value
        ->bin (into {} tuples)
        <-bin (clojure.set/map-invert ->bin)
        validator (into [:enum] (keys ->bin))]
    [{fullname {:syntax syntax, :type :enum, :default default :encode ->bin, :decode <-bin}}
     {fullname validator}]))

; ----------------------- ast -------------------------------
(defn xform-ast [ast]
  (loop [idx 0, syntax 3, package "", reg []]
    (if (>= idx (count ast)) reg ; terminate loop and return reg
        (let [form (nth ast idx)]
          (condp = (first form)
            :syntax  (recur (inc idx) (keyword (last form)) package     reg)
            :package (recur (inc idx) syntax                (last form) reg)
            :message (recur (inc idx) syntax                package     (conj reg (xform-msg syntax package form)))
            :enum    (recur (inc idx) syntax                package     (conj reg (xform-enm syntax package form)))
            (recur          (inc idx) syntax                package     reg))))))

(defn gen-registries
  "Generate codec and malli registries and return them as a tuple."
  [paths files]
  (let [rast (unnest (protoc paths files))
        ; TODO below is super inefficient, use transducer or other ways
        codec_malli_pairs (reduce into [] (map xform-ast (map val rast)))
        codec (into {} (map first) codec_malli_pairs)
        malli (into {} (map second) codec_malli_pairs)]
    [codec malli]))
