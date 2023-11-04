(ns clojobuf.core
  (:require [clojobuf.encode :refer [encode-msg]]
            [clojobuf.decode :refer [decode-msg]]
            [clojobuf.schema :refer [xform-ast vschemas-pb-types]]
            [clojobuf.util :refer [dot-qualify]]
            [malli.core :as m]
            [malli.error :as me]
            [malli.registry :as mr]
            #?(:clj [rubberbuf.core :as rc]) ; rubberbuf.core uses rubberbuf.util which uses cljs-node-io.core that is not available to cljs browser runtime
            [rubberbuf.ast-postprocess :refer [unnest]]
            #?(:cljs [sci.core]) ; manual require needed for cljs to serialize/deserialize function
            ))

(defn encode
  "Encode a message and return its protobuf binary, or return nil if msg is ill-formed.
   registry: output of clojobuf.schema.protoc
   msg-id:   message id with ns scope, e.g. :my.ns.scope/MsgA.MsgB
   msg:      message to be encoded"
  [registry msg-id msg]
  (when (m/validate [:ref (dot-qualify msg-id)] msg (second registry))
    (encode-msg (first registry)
                ((first registry) msg-id)
                msg)))

(defn decode
  "Decode a protobuf binary and return its message
   registry: output of clojobuf.schema.protoc
   msg-id:   message id with ns scope, e.g. :my.ns.scope/MsgA.MsgB
   bin:      binary to be decoded"
  [registry msg-id bin]
  (decode-msg (first registry)
              ((first registry) msg-id)
              bin))

(defn find-fault
  "Check msg against schema of msg-id in registry. Return a map of fault(s) found, or nil if no fault."
  [registry msg-id msg]
  (-> (m/explain (dot-qualify msg-id) msg (second registry))
      (me/humanize)))

(defn ->malli-registry
  "Use `input`, which is expected to be (second (protoc ... :malli-composite-registry false)), to build
   a malli registry and returns it."
  [input]
  {:registry (mr/composite-registry
              m/default-registry
              (into vschemas-pb-types input))})

; protoc needs file access which is not available to cljs browser runtime
; * for cljs browser runtime, use clojobuf.macro/protoc-macro
; * for cljs nodejs runtime, use clojobuf.nodejs/protoc
#?(:clj (defn protoc
          "Generate codec and malli registries and return them as a tuple."
          [paths files & {:keys [auto-malli-registry] :or {auto-malli-registry true}}]
          (let [rast (unnest (rc/protoc paths files))
        ; TODO below is super inefficient, use transducer or other ways
                codec_malli_pairs (reduce into [] (map xform-ast (map val rast)))
                codec (into {} (map first) codec_malli_pairs)
                malli (into {} (map second) codec_malli_pairs)
                malli (if auto-malli-registry
                        (->malli-registry malli)
                        malli)]
            [codec malli])))
