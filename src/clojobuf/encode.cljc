(ns clojobuf.encode
  (:require [clojobuf-codec.encode :refer [write-bytes write-packed write-pri]]
            [clojobuf-codec.io.writer :refer [make-writer ->bytes]]
            [clojobuf.util :refer [fid rori ktype-vtype msg|enum? map?? msg|enum-id packed? packable? default-enum-val default-pri]]))

(defn oneof? [field-def] (= (first field-def) :oneof))

(defn oneof-target?
  "field-def example: [14 :fixed32 :oneof nil]"
  [field-def] (= (nth field-def 2) :oneof))

(defn encode-prifield
  "Encode a primitive field
   field-schema: e.g. [12 :string :optional nil]"
  [writer proto2|3 field-schema value]
  (let [[fid typ rori _] field-schema]
    (cond
      (not (sequential? value))
      (when-not (and (nil? rori) ; proto3 implicit
                     (not= typ :bytes)
                     (= value (default-pri typ)))
        (write-pri writer fid typ value))

      (= typ :bytes)
      (write-pri writer fid :bytes value)

      (or (and (= proto2|3 :proto3) (packable? typ))
          (packed? field-schema))
      (write-packed writer fid typ value)

      :else
      (run! (fn [v] (write-pri writer fid typ v)) value))))

(declare encode-msg)

(defn encode-enumfield
  "Encode an enum field.
   field-schema: e.g. [1 'my.namespace/Mappy' :optional nil]
   value:        e.g. :enum-value or [:enum-value ...]"
  [writer enum-schema field-schema value]
  ; Need to handle 3 cases 
  ; (1) single value: do not encode if field is implicit & value == default
  ; (2) repeated packed
  ; (3) repeated not packed
  (let [enum->int #(get-in enum-schema [:encode %])
        ?repeated (sequential? value)]
    (if-not ?repeated
      (when-not (and (= nil (rori field-schema)) ; implicit field type
                     (= value (default-enum-val enum-schema field-schema)))
        (write-pri writer (fid field-schema) :enum (enum->int value))) ; 1
      (if (or (packed? enum-schema) (packed? field-schema))
        (write-packed writer (fid field-schema) :enum (mapv enum->int value)) ; 2
        (run! #(write-pri writer (fid field-schema) :enum (enum->int %)) value))))) ;3

(defn encode-msgfield
  "Encode a message field.
   field-schema: e.g. [1 'my.namespace/Mappy' :optional nil]
   value:        e.g. {:a :b, ..} or [{:a :b, ..} ...]"
  [writer registry msg-schema field-schema value]
  (let [enc (fn [v] (->> (encode-msg registry msg-schema v)
                         (write-bytes writer (fid field-schema))))]
    (if (sequential? value) (run! enc value) (enc value))))

(defn encode-msgfield|enumfield
  "Encode a message field or enum field.
   field-schema: e.g. [1 'my.namespace/Mappy' :optional nil]"
  [writer registry field-schema value]
  (let [msg|enum-id (msg|enum-id field-schema)
        schema ((first registry) msg|enum-id)]
    (if (= (schema :type) :msg)
      (encode-msgfield  writer registry schema field-schema value)
      (encode-enumfield writer          schema field-schema value))))

(defn encode-mapfield
  "Encode map field.
   field-schema: e.g. [1 :map [:uint32 :sint64] nil]
   value:        e.g. {123 456, 777 888, 999 1000} or [[123 456] [777 888] ...]"
  [writer registry field-schema value]
  (let [[ktype vtype] (ktype-vtype field-schema)
        msg-schema {:encode {:key [1 ktype :required nil]
                             :val [2 vtype :required nil]}}
        enc-fn (fn [[k v]] (encode-msgfield writer registry msg-schema field-schema {:key k :val v}))]
    (run! enc-fn (seq value))))

(defn encode-msg
  "Encode a message and return its protobuf binary.
   schema: schema of message value, e.g.
            {:syntax :proto3,
             :type :message,
             :encode {...},
             :decode {...}}"
  [registry schema msg]
  (let [writer (make-writer)
        proto2|3 (schema :syntax)
        msg-schema (schema :encode)
        keys-vals (seq msg)]
    (loop [idx 0]
      (when (< idx (count keys-vals))
        (let [[k v] (nth keys-vals idx)
              field-schema (msg-schema k)]
          (when (and (not (nil? v)) (not= k :?))
            (cond
              (oneof? field-schema)
            ; value of oneof == key of actual field in msg
              (let [target-field-schema (msg-schema v)]
                (if (msg|enum? target-field-schema)
                  (encode-msgfield|enumfield writer registry target-field-schema (msg v))
                  (encode-prifield writer proto2|3 target-field-schema (msg v))))

              (msg|enum? field-schema)
              (when-not (oneof-target? field-schema)
                (encode-msgfield|enumfield writer registry field-schema v))

              (map?? field-schema)
              (encode-mapfield writer registry field-schema v)

              :else
              (when-not (oneof-target? field-schema)
                (encode-prifield writer proto2|3 field-schema v)))))
        (recur (inc idx))))
    (->bytes writer)))
