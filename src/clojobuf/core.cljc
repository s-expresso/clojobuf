(ns clojobuf.core
  (:require [clojobuf.encode :refer [encode-msg]]
            [clojobuf.decode :refer [decode-msg]]
            [clojobuf.schema :refer [xform-ast vschemas-pb-types vschemas-update-msg-field-presence vschemas-update-generator-fmap vschemas-make-defaults]]
            [clojobuf.util :as util]
            [malli.core :as m]
            [malli.error :as me]
            [malli.generator :as mg]
            [malli.registry :as mr]
            #?(:clj [rubberbuf.core :as rc]) ; rubberbuf.core uses rubberbuf.util which uses cljs-node-io.core that is not available to cljs browser runtime
            [rubberbuf.ast-postprocess :refer [unnest]]))

(defn find-fault
  "Check msg against schema of msg-id in registry. Return a map of fault(s) found, or nil if no fault."
  [registry msg-id msg]
  (-> (m/explain (util/dot-qualify msg-id) msg (second registry))
      (me/humanize)))

(defn default-msg
  [registry msg-id]
  (get-in (second registry) [:defaults (util/dot-qualify msg-id)]))

(defn encode
  "Encode a message and return its protobuf binary, or return nil if msg is ill-formed.
   registry: output of clojobuf.schema.protoc
   msg-id:   message id with ns scope, e.g. :my.ns.scope/MsgA.MsgB
   msg:      message to be encoded"
  [registry msg-id msg]
  (when (m/validate [:ref (util/dot-qualify msg-id)] msg (second registry))
    (encode-msg registry
                ((first registry) msg-id)
                msg)))

(defn decode
  "Decode a protobuf binary and return its message, or return nil if decoded msg is ill-formed.
   registry: output of clojobuf.schema.protoc
   msg-id:   message id with ns scope, e.g. :my.ns.scope/MsgA.MsgB
   bin:      binary to be decoded"
  [registry msg-id bin]
  (let [msg (decode-msg registry
                        ((first registry) msg-id)
                        bin)]
    (when (m/validate [:ref (util/dot-qualify msg-id)] msg (second registry))
      (merge (default-msg registry msg-id) msg))))


(defn generate
  [registry msg-id]
  (merge (default-msg registry msg-id)
         (mg/generate [:ref (util/dot-qualify msg-id)] (second registry))))

(defn- ->complete-malli-schema
  "Add value schema to a composite registry. Return the new registry."
  [schema] (into vschemas-pb-types schema))


(defn ->malli-registry
  "Use `schema`, which is expected to be (second (protoc ... :malli-composite-registry false)), to build
   a malli registry and returns it."
  [schema]
  (let [schema (vschemas-update-generator-fmap schema)]
    {:registry (mr/composite-registry
                m/default-registry
                (->complete-malli-schema schema))
     :defaults (vschemas-make-defaults schema)}))

; protoc needs file access which is not available to cljs browser runtime
; * for cljs browser runtime, use clojobuf.macro/protoc-macro
; * for cljs nodejs runtime, use clojobuf.nodejs/protoc
#?(:clj (defn protoc
          "Generate codec and malli registries and return them as a tuple."
          [paths files & {:keys [auto-malli-registry] :or {auto-malli-registry true}}]
          (let [rast (unnest (rc/protoc paths files))
                codec_malli_pairs (transduce (map (comp xform-ast val)) into [] rast)
                codec             (transduce (map first)                into {} codec_malli_pairs)
                malli             (transduce (map second)               into {} codec_malli_pairs)
                malli             (vschemas-update-msg-field-presence malli)
                malli (if auto-malli-registry
                        (->malli-registry malli)
                        malli)]
            [codec malli])))
