(ns clojobuf.schema
  (:require [clojobuf.constant :refer [sint32-max sint32-min sint53-max sint53-min sint64-max sint64-min uint32-max uint32-min uint64-max uint64-min]]
            [clojobuf.util :refer [dot-qualify]]
            [clojure.set :refer [map-invert]]))

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
(def vschemas-pb-types {:int32     [:int {:min sint32-min, :max sint32-max}]
                        :sint32    [:int {:min sint32-min, :max sint32-max}]
                        :sfixed32  [:int {:min sint32-min, :max sint32-max}]
                        :uint32    [:int {:min uint32-min, :max uint32-max}]
                        :fixed32   [:int {:min uint32-min, :max uint32-max}]
                        :int64     [:int {:min sint64-min, :max sint64-max}]
                        :sint64    [:int {:min sint64-min, :max sint64-max}]
                        :sfixed64  [:int {:min sint64-min, :max sint64-max}]
                        :uint64    [:or [:int {:min 0}] [:fn (fn [v] (and (= (type v) clojure.lang.BigInt)
                                                                          (>= v 0)
                                                                          (<= v uint64-max)))]]
                        :fixed64   [:or [:int {:min 0}] [:fn (fn [v] (and (= (type v) clojure.lang.BigInt)
                                                                          (>= v 0)
                                                                          (<= v uint64-max)))]]
                        :bytes     #?(:clj 'bytes? :cljs [:fn (fn [v] (= js/Uint8Array (type v)))])})

(defn- get-malli-type [typ]
  (cond
    (string? typ) [:ref (dot-qualify (keyword typ))]
    (= typ :float) :double ; TODO is this correct?
    (= typ :bool) :boolean
    #_(#{:int32 :uint32 :sint32 :int64 :uint64 :sint64 :fixed32 :fixed64 :sfixed32 :sfixed64 :string :double :bytes} typ)
    :else typ))

(defn- vxform-field [[_ rori typ name field-id options]]
  (condp = rori
    :required [(keyword name) (get-malli-type typ)]
    :repeated [(keyword name) {:optional true} [:vector (get-malli-type typ)]]
    [(keyword name) {:optional true} (get-malli-type typ)]))

(defn- vxform-map-field [[_ ktype vtype name field-id options]]
  [(keyword name) {:optional true} [:map-of (get-malli-type ktype) (get-malli-type vtype)]])

(defn- vxform-oneof-field [[_ typ name field-id options]]
  [(keyword name) {:optional true} (get-malli-type typ)])

(defn- vxform-oneof
  "[:oneof 'either'
       [:oneofField :string 'name' 1 nil]
       [:oneofField 'Msg' 'msg' 2 nil]]
   =>
   [ [[:either [:enum :name :msg]]
      [:name string?]
      [:msg [:ref :Msg]]]
     [:fn '(fn [kvs] (if-let [oneof-target (kvs :either)]
                     (contains? kvs oneof-target)
                     true))] ]"
  [[_ name & forms]]
  [(into
    [[(keyword name) {:optional true} (into [:enum] (map #(keyword (nth % 2))) forms)]]
    (map vxform-oneof-field forms))
   [:fn (fn [kvs] (if-let [oneof-target (kvs (keyword name))]
                    (contains? kvs oneof-target)
                    true))]])

(defn- vld-msg-children-processor
  "msg-children are child nodes within message; each can be :field, :mapField, :oneof or :option."
  [msg-children]
  (loop [idx 0, main [:map {:closed true}], funcs []]
    (if (< idx (count msg-children))
      (let [msg-child (nth msg-children idx)]
        (condp = (first msg-child)
          :field    (recur (inc idx), (conj main (vxform-field msg-child)),     funcs)
          :mapField (recur (inc idx), (conj main (vxform-map-field msg-child)), funcs)
          :oneof (let [[m f] (vxform-oneof msg-child)]
                   (recur (inc idx), (into main m) (conj funcs f)))
          (recur (inc idx) main funcs)))
      (if (empty? funcs)
        main
        (into [:and main] funcs)))))

; -------------------- msg main -----------------------------
(defn- xform-msg [syntax package form]
  (let [fullname (qualify-name package (second form))
        ->bin (reduce fwd-msg-child-reducer {} (drop 2 form))
        <-bin (reduce bwd-msg-child-reducer {} (drop 2 form))
        validator (vld-msg-children-processor (drop 2 form))]
    [{fullname {:syntax syntax :type :msg :encode ->bin :decode <-bin}}
     {(dot-qualify fullname) validator}]))

; ---------------------- enum -------------------------------
(defn- enum-child-reducer
  "enum-child is child node within enum; it can be :enumField or :option."
  [prev enum-child] (if (= (first enum-child) :enumField)
                      (conj prev [(keyword (second enum-child)) (nth enum-child 2)])
                      prev))

(defn- xform-enm [syntax package enm]
  (let [fullname  (qualify-name package (second enm))
        tuples (reduce enum-child-reducer [] (drop 2 enm))
        default (-> tuples first first) ; for enum, the first entry is the default value
        ->bin (into {} tuples)
        <-bin (map-invert ->bin)
        validator (into [:enum] (keys ->bin))]
    [{fullname {:syntax syntax, :type :enum, :default default :encode ->bin, :decode <-bin}}
     {(dot-qualify fullname) validator}]))

; ----------------------- ast -------------------------------
(defn xform-ast
  "returns a vector of vec2, e.g.
    [ [{:a.b/m1 ...} {:a.b/m1 ...}]
      [{:a.b/m2 ...} {:a.b/m2 ...}]
      ...]
   where:
     * (first vec2)  is n single entry of codec schema
     * (second vec2) is a single entry of malli schema"
  [ast]
  (loop [idx 0, syntax 3, package "", reg [[nil vschemas-pb-types]]] ; inject vschemas-pb-types
    (if (>= idx (count ast)) reg ; terminate loop and return reg
        (let [form (nth ast idx)]
          (condp = (first form)
            :syntax  (recur (inc idx) (keyword (last form)) package     reg)
            :package (recur (inc idx) syntax                (last form) reg)
            :message (recur (inc idx) syntax                package     (conj reg (xform-msg syntax package form)))
            :enum    (recur (inc idx) syntax                package     (conj reg (xform-enm syntax package form)))
            (recur          (inc idx) syntax                package     reg))))))
