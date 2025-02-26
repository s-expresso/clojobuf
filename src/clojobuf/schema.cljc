(ns clojobuf.schema
  (:require [clojobuf.constant :refer [sint32-max sint32-min sint53-max sint53-min sint64-max sint64-min uint32-max uint32-min uint64-max uint64-min]]
            [clojobuf.util :refer [dot-qualify default-opt default-pri field-presence-opt]]
            [clojure.set :refer [map-invert]]
            [clojure.test.check.generators :as gen]
            [flatland.ordered.map :refer [ordered-map]]
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

(defn- get-opt-malli-type [typ]
  (cond
    (string? typ) [:maybe [:ref (dot-qualify (keyword typ))]]
    (= typ :float) [:maybe :double] ; TODO is this correct?
    (= typ :bool) [:maybe :boolean]
    #_(#{:int32 :uint32 :sint32 :int64 :uint64 :sint64 :fixed32 :fixed64 :sfixed32 :sfixed64 :string :double :bytes} typ)
    :else [:maybe typ]))

(defn- vxform-field [syntax [_ rori typ name field-id options]]
  (let [out (if (= syntax :2023)
              ; protobuf edition 2023
              (condp = rori
                :repeated [(keyword name) {:optional true :presence :repeated} [:vector (get-malli-type typ)]]
                [(keyword name) (condp = (field-presence-opt options)
                                  :IMPLICIT        {:optional true  :presence :implicit}
                                  :LEGACY_REQUIRED {                :presence :required}
                                                   {:optional true  :presence :optional}) (get-malli-type typ)])
              ; (or (= syntax :proto2) (= syntax :proto3))
              (condp = rori
                :required [(keyword name) {               :presence :required} (get-malli-type typ)]
                :repeated [(keyword name) {:optional true :presence :repeated} [:vector (get-malli-type typ)]]
                :optional [(keyword name) {:optional true :presence :optional} (get-opt-malli-type typ)]
                          [(keyword name) {:optional true :presence :implicit} (get-malli-type typ)]))] 
    (if-let [default (when (not= syntax :proto3) ; proto3 doesn't allow overriding of default value
                       (default-opt options))]
      [(first out) (assoc (second out) :default default) (last out)]
      out)))

(defn- vxform-map-field [syntax [_ ktype vtype name field-id options]]
  [(keyword name) {:optional true
                   :presence :map} [:maybe [:map-of (get-malli-type ktype) (get-malli-type vtype)]]])

(defn- vxform-oneof-field [syntax [_ typ name field-id options]]
  [(keyword name) {:optional true
                   :presence :oneof-field} (get-opt-malli-type typ)])

(defn- vxform-oneof
  "[:oneof 'either'
       [:oneofField :string 'name' 1 nil]
       [:oneofField 'Msg' 'msg' 2 nil]]
   =>
   [ [[:either [:enum :name :msg]]
      [:name string?]
      [:msg [:ref :Msg]]]
     [:oneof :either [:name :msg]] ]"
  [syntax [_ name & forms]]
  (let [oneof-key (keyword name)
        targets (map #(keyword (nth % 2)) forms)]
    [(into
      [[oneof-key {:optional true
                   :presence :oneof} (into [:enum] targets)]]
      (map #(vxform-oneof-field syntax %) forms))
     [:oneof oneof-key (into [] targets)]]))

(defn- vld-msg-children-processor
  "msg-children are child nodes within message; each can be :field, :mapField, :oneof or :option."
  [syntax msg-children]
  ; :? can be injected during decoding, hence validation schema has a generic entry to ignore it
  (loop [idx 0, main [:map {:closed true} [:? {:optional true :presence :? :default nil} :any]], funcs []]
    (if (< idx (count msg-children))
      (let [msg-child (nth msg-children idx)]
        (condp = (first msg-child)
          :field    (recur (inc idx), (conj main (vxform-field syntax msg-child)),     funcs)
          :field+   (recur (inc idx), (conj main (vxform-field syntax msg-child)),     funcs)
          :mapField (recur (inc idx), (conj main (vxform-map-field syntax msg-child)), funcs)
          :oneof (let [[m f] (vxform-oneof syntax msg-child)]
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
        validator (vld-msg-children-processor syntax (drop 2 form))]
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
  (loop [idx 0, syntax :proto3, package "", reg []]
    (if (>= idx (count ast)) reg ; terminate loop and return reg
        (let [form (nth ast idx)]
          (condp = (first form)
            :syntax  (recur (inc idx) (keyword (last form)) package     reg) ; :proto2 or :proto3
            :edition (recur (inc idx) (keyword (last form)) package     reg) ; :2023
            :package (recur (inc idx) syntax                (last form) reg)
            :message (recur (inc idx) syntax                package     (conj reg (xform-msg syntax package form)))
            :enum    (recur (inc idx) syntax                package     (conj reg (xform-enm syntax package form)))
            (recur          (inc idx) syntax                package     reg))))))

; ------------------ post-processing ---------------------------
(defn vschemas-update-msg-field-presence
  "Implicit message field presence needs to be interpeted as purely optional.
   Since the referenced field can be an enum or a message, this is performed
   after xform-ast as a separate step.

   For example, the following input
     {:my.ns/MsgA [:map
                    {:closed true}
                    [:enum_val {:optional true
                                :presence :implicit} [:ref :my.ns/Enum]]
                    [:msg_val {:optional true
                               :presence :implicit} [:ref :my.ns/MsgB]]]
      :my.ns/Enum [:enum :ZERO :ONE]
      :my.ns/MsgB [:map
                    {:closed true}
                    [:field :int32]]}
   will have
     [:msg_val {... :presence :implicit} [:ref :my.ns/MsgB]]
   updated to
     [:msg_val {... :presence :optional} [:ref :my.ns/MsgB]]"
  [vschemas]
  (let [update-implicit-property
        (fn [form] (let [properties (assoc (second form) :presence :optional)]
                     [(first form) properties (last form)]))]
    (sp/transform [sp/ALL-WITH-META
                   (sp/nthpath 1)
                   ; visit all elements of message (:map) type
                   (sp/cond-path #(= :map (first %)) sp/ALL-WITH-META
                                 #(and (= :and (-> % first)) ; if message contains oneof, top level is [:and [:map ...] ...]
                                       (= :map (-> % second first))) [(sp/nthpath 1) sp/ALL-WITH-META])

                   ; only visit implicit message field
                   (sp/if-path vector? sp/STAY)                                        ; filter out non fields
                   (sp/if-path #(-> % last vector?) sp/STAY)                           ; filter out primitive fields
                   (sp/if-path #(= 3 (count %)) sp/STAY)                               ; filter out required fields which are w/o property
                   
                   (sp/if-path #(= :implicit (get (second %) :presence)) sp/STAY)      ; filter out non implicit fields
                   (sp/if-path #(= :ref (-> % last first)) sp/STAY)                    ; filter out non :ref fields
                   (sp/if-path #(not= :enum (-> % last last vschemas first)) sp/STAY)] ; filter out if referenced type is :enum (other possibiilities :map & :and)
                  update-implicit-property
                  vschemas)))

(defn vschemas-update-generator-fmap
  "See vschemas-update-msg-field-presence for example input.
   Changes {... {:close true} ...} to {... {:closed true, :gen/fmap #(dissoc % :?)} ...}"
  [vschemas]
  (let [xform (fn [form] (assoc form :gen/fmap #(dissoc % :?)))]
    (sp/transform [sp/ALL-WITH-META
                   (sp/nthpath 1)
                   ; visit all elements of message (:map) type
                   (sp/cond-path #(= :map (first %)) (sp/nthpath 1)
                                 #(and (= :and (-> % first)) ; if message contains oneof, top level is [:and [:map ...] ...]
                                       (= :map (-> % second first))) (sp/nthpath 1 1))
                   ; only visit property map
                   (sp/if-path map? sp/STAY)]
                  xform
                  vschemas)))

(defn vschemas-make-defaults
  "Make default values from validation schema."
  [vschemas]
  (let [get-default (fn [field-schema-maybe]
                      ; valid field-schema-maybe example:
                      ; [:enum_val {:optional true :presence :implicit} [:ref :my.ns/Enum]]
                      (if (and (vector?                                  field-schema-maybe)
                                 (= 3                               (count field-schema-maybe))  ; always true
                                 (map?                             (second field-schema-maybe))) ; always true
                        (let [presence ((second field-schema-maybe) :presence)]
                          (cond
                            (contains? #{:implicit :required} presence)
                            (let [typ (nth field-schema-maybe 2)
                                  ; TODO implicit field shouldn't have :maybe prepended, hence no need to strip
                                  typ (if (and (vector? typ) (= :maybe (first typ))) (second typ) typ)]
                              (cond
                                (= :bytes typ) #?(:clj (byte-array 0)
                                                  :cljs (js/Uint8Array.))
                                (= :boolean typ) false
                                (keyword? typ) (default-pri typ)
                                (vector? typ) (let [schema (vschemas (second typ))]
                                            ; only handle enum because implicit message is actually optional
                                            ; and we do not (may revisit?) support default value for message
                                                (when (= :enum (first schema)) (second schema)))))
                            (= :optional presence) nil
                            (= :repeated presence) []
                            (= :map presence) {}
                            (= :oneof presence) nil
                            (= :oneof-field presence) nil
                            :else :-silent-)) ; reachable by :?
                        :-silent-))
        xform-msg (fn [form]
                    ; form example:
                    ; [:map
                    ;  {:closed true}
                    ;  [:enum_val {:optional true :presence :implicit} [:ref :my.ns/Enum]]
                    ;  [:int_val {:optional true :presence :implicit} :int32]]
                    (loop [idx 1, xformed (ordered-map)]
                      (if (>= idx (count form)) xformed ; terminate loop and return xformed
                          (let [field-schema-maybe (nth form idx)]
                            (recur (inc idx) (let [default (get-default field-schema-maybe)]
                                               (if (= :-silent- default)
                                                 xformed
                                                 (assoc xformed (first field-schema-maybe) default))))))))
        xform (fn [form] 
                (cond
                  (= :map (first form)) (xform-msg form)
                  (= :and (first form)) (xform-msg (second form))
                  :else nil))]
    (reduce-kv (fn [m k v] (let [xformed (xform v)]
                             (if (nil? xformed)
                               m
                               (assoc m k xformed))))
               (ordered-map)
               vschemas)))
