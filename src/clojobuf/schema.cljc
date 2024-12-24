(ns clojobuf.schema
  (:require [clojobuf.constant :refer [sint32-max sint32-min sint53-max sint53-min sint64-max sint64-min uint32-max uint32-min uint64-max uint64-min]]
            [clojobuf.util :refer [dot-qualify]]
            [clojure.set :refer [map-invert]]
            [clojure.test.check.generators :as gen]
            [malli.core :as m]
            [com.rpl.specter :as sp]))

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
  "msg-child is a child node within message; it can be :field, :mapField, :oneof or other types that we don't care."
  [prev msg-child]
  (conj prev (condp = (first msg-child)
               :field  (fxform-field msg-child)
               :field+ (fxform-field msg-child)
               :mapField (fxform-map-field msg-child)
               :oneof (fxform-oneof msg-child)
               nil)))

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
  "msg-child a child node within message; it can be :field, :mapField, :oneof or other types that we don't care."
  [prev msg-child]
  (conj prev (condp = (first msg-child)
               :field (bxform-field msg-child)
               :field+ (bxform-field msg-child)
               :mapField (bxform-map-field msg-child)
               :oneof (bxform-oneof msg-child)
               nil)))

(def OneOf
  (m/-simple-schema
   {:type `OneOf
    :compile (fn [_properties [oneof targets] _options]
               (when-not (and (keyword? oneof)
                              (every? keyword? targets))
                 (m/-fail! ::invalid-children {:oneof oneof :targets targets}))
               {:pred (fn [kvs] (if-let [target (kvs oneof)]
                                  (and (contains? kvs target)
                                       (= 1 (count (clojure.set/intersection (set targets) (set (keys kvs))))))
                                  (= 0 (count (clojure.set/intersection (set targets) (set (keys kvs)))))))
                :min 2 ;; at least 1 child
                :max 2 ;; at most 1 child
                :type-properties  {:error/fn (fn [_error _reg] {oneof (str "oneof condition not met: only this field's target can be set but not the other targets" )})
                                   :error/path [oneof]
                                   :gen/gen (gen/return (let [target (rand-nth targets)] {oneof target, target nil}))}})}))

(def Bytes
  (m/-simple-schema
   {:type `Bytes
    :compile (fn [_properties [] _options]
               {:pred #?(:clj bytes?
                         :cljs #(= js/Uint8Array (type %)))
                :min 0 ;; no child
                :max 0 ;; no child 
                :type-properties {:error/message "must be byte array"
                                  :gen/gen (gen/return #?(:clj (byte-array 0)
                                                          :cljs (js/Uint8Array.)))}})}))

(def Uint64
  (m/-simple-schema
   {:type `Uint64
    :compile (fn [_properties [] _options]
               {:pred #?(:clj #(or (and (int? %) (>= % 0)) (and (= (type %) clojure.lang.BigInt)
                                                                (>= % 0)
                                                                (<= % uint64-max)))
                         :cljs #(and (int? %) (>= % 0)))
                :min 0 ;; no child
                :max 0 ;; no child 
                :type-properties {:error/message "unsigned 64-bit integer"
                                  :gen/gen #?(:clj (gen/large-integer* {:min 0, :max sint64-max})
                                              :cljs (gen/large-integer* {:min 0, :max sint53-max}))}})}))
                                  
; -------------------- msg validator -----------------------------
(def vschemas-pb-types {:int32     [:int {:min sint32-min, :max sint32-max}]
                        :sint32    [:int {:min sint32-min, :max sint32-max}]
                        :sfixed32  [:int {:min sint32-min, :max sint32-max}]
                        :uint32    [:int {:min uint32-min, :max uint32-max}]
                        :fixed32   [:int {:min uint32-min, :max uint32-max}]
                        :int64     [:int {:min sint64-min, :max sint64-max}]
                        :sint64    [:int {:min sint64-min, :max sint64-max}]
                        :sfixed64  [:int {:min sint64-min, :max sint64-max}]
                        :uint64    [Uint64 {:default 0}]
                        :fixed64   [Uint64 {:default 0}]
                        :bytes     [Bytes {:default #?(:clj (byte-array 0)
                                                       :cljs (js/Uint8Array.))}]
                        :oneof     OneOf})

(def defaults {:string (constantly "")
               :int {constantly 0}
               :boolean (constantly false)
               :double (constantly 0.0)})

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
    :optional [(keyword name) {:optional true} (get-malli-type typ)]
    [(keyword name) (if (string? typ) {:optional true} {}) (get-malli-type typ)]))

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
     [:oneof :either [:name :msg]] ]"
  [[_ name & forms]]
  (let [oneof-key (keyword name)
        targets (map #(keyword (nth % 2)) forms)]
    [(into
      [[oneof-key {:optional true} (into [:enum] targets)]]
      (map vxform-oneof-field forms))
     [:oneof oneof-key (into [] targets)]]))

(defn- vld-msg-children-processor
  "msg-children are child nodes within message; each can be :field, :mapField, :oneof or :option."
  [msg-children]
  (loop [idx 0, main [:map {:closed true}], funcs []]
    (if (< idx (count msg-children))
      (let [msg-child (nth msg-children idx)]
        (condp = (first msg-child)
          :field    (recur (inc idx), (conj main (vxform-field msg-child)),     funcs)
          :field+   (recur (inc idx), (conj main (vxform-field msg-child)),     funcs)
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
  (let [fullname (qualify-name package (second enm))
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
  (loop [idx 0, syntax 3, package "", reg []]
    (if (>= idx (count ast)) reg ; terminate loop and return reg
        (let [form (nth ast idx)]
          (condp = (first form)
            :syntax  (recur (inc idx) (keyword (last form)) package     reg)
            :package (recur (inc idx) syntax                (last form) reg)
            :message (recur (inc idx) syntax                package     (conj reg (xform-msg syntax package form)))
            :enum    (recur (inc idx) syntax                package     (conj reg (xform-enm syntax package form)))
            (recur          (inc idx) syntax                package     reg))))))

; ------------------ post-processing ---------------------------
(defn vschemas-update-msg-field-presence
  "Implicit message field presence needs to be interpeted as optional. Since
   the referenced field can be an enum or a message, this is performed after
   xform-ast as a separate step.

   For example, the following input
     {:my.ns/MsgA [:map
                    {:closed true}
                    [:enum_val [:ref :my.ns/Enum]]
                    [:msg_val [:ref :my.ns/MsgB]]]
      :my.ns/Enum [:enum :ZERO :ONE]
      :my.ns/MsgB [:map
                    {:closed true}
                    [:field :int32]]}
   will have
     [:msg_val [:ref :my.ns/MsgB]]
   updated to
     [:msg_val {:optional true} [:ref :my.ns/MsgB]]"
  [vschemas]
  (let [inject-optional-property
        (fn [form] (let [properties (if (= 2 (count form))
                                       {:optional true}
                                       (assoc (second form) :optional true))]
                     [(first form) properties (last form)]))]
    (sp/transform [sp/ALL-WITH-META
                   (sp/nthpath 1)
                   ; visit all elements of message (:map) type
                   (sp/if-path #(= :map (first %)) sp/ALL-WITH-META)
                   ; only visit implicit message field
                   (sp/if-path vector? sp/STAY)                                    ; filter out non fields
                   (sp/if-path #(-> % last vector?) sp/STAY)                       ; filter out primitive fields
                   (sp/if-path #(or (= 2 (count %))
                                    (nil? (get (second %) :optional))) sp/STAY)    ; filter out non implicit fields
                   (sp/if-path #(= :ref (-> % last first)) sp/STAY)                ; filter out non :ref fields
                   (sp/if-path #(= :map (-> % last last vschemas first)) sp/STAY)] ; filter out if referenced type is not message (i.e. enum)
                  inject-optional-property
                  vschemas)))
