(ns clojobuf.core
  (:require [clojobuf.encode :refer [encode-msg]]
            [clojobuf.decode :refer [decode-msg]]
            [clojobuf.schema :refer [xform-ast]]
            [malli.core :as m]
            [malli.error :as me]
            [malli.registry :as mr]
            [rubberbuf.core :as rc]
            [rubberbuf.ast-postprocess :refer [unnest]]))

(defn encode
  "Encode a message and return its protobuf binary, or return nil if msg is ill-formed.
   registry: output of clojobuf.schema.protoc
   msg-id:   message id with ns scope, e.g. :my.ns.scope/MsgA.MsgB
   msg:      message to be encoded"
  [registry msg-id msg]
  (when (m/validate [:ref msg-id] msg (second registry))
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
  (-> (m/explain msg-id msg (second registry))
      (me/humanize)))

(defn protoc
  "Generate codec and malli registries and return them as a tuple."
  [paths files & {:keys [malli-composite-registry] :or {malli-composite-registry true}}]
  (let [rast (unnest (rc/protoc paths files))
        ; TODO below is super inefficient, use transducer or other ways
        codec_malli_pairs (reduce into [] (map xform-ast (map val rast)))
        codec (into {} (map first) codec_malli_pairs)
        malli (into {} (map second) codec_malli_pairs)
        malli (if malli-composite-registry {:registry (mr/composite-registry
                                                       m/default-registry
                                                       malli)}
                  malli)]
    [codec malli]))
