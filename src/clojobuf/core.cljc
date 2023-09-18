(ns clojobuf.core
  (:require [clojobuf.encode :refer [encode-msg]]
            [clojobuf.decode :refer [decode-msg]]
            [clojobuf.schema :refer [xform-ast]]
            [rubberbuf.core :refer [protoc]]
            [rubberbuf.ast-postprocess :refer [unnest]]))

(defn encode
  "Encode a message and return its protobuf binary.
   codec-registry: registry of codec from clojobuf.schema.gen-registries
   msg-id:         message id with ns scope, e.g. :my.ns.scope/MsgA.MsgB
   msg:            message to be encoded"
  [codec-registry msg-id msg]
  (encode-msg codec-registry
              (codec-registry msg-id)
              msg))

(defn decode
  "Decode a protobuf binary and return its message
   codec-registry: registry of codec from clojobuf.schema.gen-registries
   msg-id:         message id with ns scope, e.g. :my.ns.scope/MsgA.MsgB
   bin:            binary to be decoded"
  [codec-registry msg-id bin]
  (decode-msg codec-registry
              (codec-registry msg-id)
              bin))

(defn gen-registries
  "Generate codec and malli registries and return them as a tuple."
  [paths files]
  (let [rast (unnest (protoc paths files))
        ; TODO below is super inefficient, use transducer or other ways
        codec_malli_pairs (reduce into [] (map xform-ast (map val rast)))
        codec (into {} (map first) codec_malli_pairs)
        malli (into {} (map second) codec_malli_pairs)]
    [codec malli]))
