(ns clojobuf.decode
  (:require [clojobuf-codec.io.reader :refer [make-reader available?]]
            [clojobuf-codec.decode :refer [read-len-coded-bytes read-packed read-pri read-raw-wire read-tag]]
            [clojobuf.util :refer [fname typ ktype-vtype repeated? msg|enum? map?? msg|enum-id]]))

(defn oneof? [field-def] (let [form (nth field-def 2)]
                           (and (sequential? form) (= (first form) :oneof))))

(defn oneof-label [field-def] (-> field-def (nth 2) second))

(defn wt-pt-compatible?
  "Test compatibility of Wire type and primitive type"
  [wire-type pri-type]
  (condp = wire-type
    0 (#{:int32 :int64 :uint32 :uint64 :sint32 :sint64 :bool :enum} pri-type)
    1 (#{:fixed64 :sfixed64 :double} pri-type)
    2 (#{:string :bytes} pri-type)
    5 (#{:fixed32 :sfixed32 :float} pri-type)))

(declare decode-msg)

(defn decode-priField
  "Return a name/value pair like [:name 123] or [:name [123 456 789]], or return
   field-id/wire-value pair like [7 123] or [7 <binary>]) if field's primitive
   type is incompatible with the decoded wire-type."
  [reader wire-type fid field-schema]
  (let [pri-type (typ field-schema)]
    (cond
      ; expected wire-type
      (wt-pt-compatible? wire-type pri-type)
      [(fname field-schema) (read-pri reader pri-type)]
      ; packed
      (= wire-type 2)
      [(fname field-schema) (read-packed reader pri-type)]
      ; unexpected wire-type
      :else (do
              (println "unexpected")
              [fid (read-raw-wire reader wire-type)]))))

(defn decode-mapField
  "Return a map with 1 entry, e.g. {23 12345}, or return field-id/wire-value pair
   like [7 123] if decoded wire-type is incompatible with map.
   
   field-schema: [1 :map [:uint32 :sint64] nil]"
  [reader codec-registry wire-type fid field-schema]
  (condp = wire-type
    2 (let [[ktype vtype] (ktype-vtype field-schema)
            msg-schema {:decode {1 [:key ktype :na nil]
                                 2 [:val vtype :na nil]}}
            bin (read-len-coded-bytes reader)
            kv-msg (decode-msg codec-registry msg-schema bin)
            kv {(kv-msg :key) (kv-msg :val)}]
        [(fname field-schema) kv])
    [fid (read-raw-wire reader wire-type)]))

(defn decode-enumField
  "Return a name/value pair like [:name :enum-vale] or [:name [:enum-v1 :enum-v2
   :enum-v3]], or return field-id/wire-value pair like [7 123] or [7 <binary>]) if
   field's primitive type is incompatible with the decoded wire-type."
  [reader wire-type enum-schema fid field-schema]
  (let [dmap (enum-schema :decode)
        deco (fn [int-val] (if-let [enum-kw (dmap int-val)] enum-kw int-val))]
    (condp = wire-type
      ; packed
      2 (let [ints (read-packed reader :int32)
              vals (run! deco ints)]
          [(fname field-schema) vals])
      ; expected wire-type
      0 [(fname field-schema) (deco (read-pri reader :enum))]
      ; unexpected wire-type
      [fid (read-raw-wire reader wire-type)])))

(defn decode-msgField
  "Return a name/value pair like [:name {:msg-field1 123, :msg-field2 456, ...}]
   or [:name [{:msg-field1, ...}, {...}]], or return field-id/wire-value pair like
   [7 123] or [7 <binary>]) if field's primitive type is incompatible with the
   decoded wire-type."
  [reader codec-registry wire-type msg-schema fid field-schema]
  (if (= wire-type 2)
    (let [bin (read-len-coded-bytes reader)]
      [(fname field-schema) (decode-msg codec-registry msg-schema bin)])
    [fid (read-raw-wire reader wire-type)]))

(defn decode-msgField|enumField
  "Calls decode-msgField or decode-enumField based on type detected."
  [reader codec-registry wire-type fid field-schema]
  (let [msg|enum-id (msg|enum-id field-schema)
        schema (codec-registry msg|enum-id)]
    (if (= (schema :type) :msg)
      (decode-msgField  reader codec-registry wire-type schema fid field-schema)
      (decode-enumField reader                wire-type schema fid field-schema))))

(defn merge-msgfield
  "Merge field into fields, where fields is a map and field can be one of the following types:
   (1) primitive: [:name v] or [:name [v1 v2 ...]]
   (2) msg:       [:name {...}] or [:name [{...}, {...}, ...]]
   (3) enum:      [:name :enum-v] or [:name [:enum-v1 :enum-v2 ...]]
                  [:name 7] or [:name [7 :enum-v1 9]] also possible if int->enum mapping is N/A for that int value.
   (4) map:       [:name {7 '1234'}], where key or value or both can be nil if binary doesn't conform to expected format
   (5) unknown:   [<field-id> <wire-value>]

   Uknown field is concat into fields as {:? [<field-id> <wire-type> <wire-value>]}"
  [fields field wire-type ?repeated ?map]
  (let [[fid val] field]
    (cond
      (number? fid) (merge-with into fields {:? [[fid wire-type val]]})
      ?repeated     (let [val (if (sequential? val) val [val])]
                      (merge-with into fields {fid val}))
      ?map          (merge-with into fields {fid val})
      :else         (let [val (if (sequential? val) (last val) val)] ; val shouldn't be sequential for non-repeated field, but we just check anyway to be safe
                      (merge fields {fid val})))))

(defn decode-msg [codec-registry schema bin]
  (let [reader (make-reader bin)
        dec-schema (schema :decode)]
    (loop [msg {}]
      (if (available? reader)
        (let [[fid wire-type] (read-tag reader)
              field-schema (dec-schema fid)
              tuple (cond
                      (nil?      field-schema) [fid (read-raw-wire        reader                wire-type)]
                      (msg|enum? field-schema) (decode-msgField|enumField reader codec-registry wire-type fid field-schema)
                      (map??     field-schema) (decode-mapField           reader codec-registry wire-type fid field-schema)
                      :else                    (decode-priField           reader                wire-type fid field-schema))
              merged (merge-msgfield msg tuple wire-type (repeated? field-schema) (map?? field-schema))
              merged (if (oneof? field-schema) (conj merged {(oneof-label field-schema) (fname field-schema)}) merged)]
          (recur merged)) ; TODO need a better conj to handle repeated + inject oneof-selector
        msg))))

(defn decode
  "Decode a protobuf binary and return its message
   codec-registry: registry of codec from clojobuf.schema.gen-registries
   msg-id:         message id with ns scope, e.g. :my.ns.scope/MsgA.MsgB
   bin:            binary to be decoded"
  [codec-registry msg-id bin]
  (decode-msg codec-registry
              (codec-registry msg-id)
              bin))
