(ns clojobuf.nodejs
  (:require [clojobuf.core :refer [->malli-registry]]
            [clojobuf.schema :refer [xform-ast vschemas-update-msg-field-presence]]
            [rubberbuf.core :as rc] ; rubberbuf.core uses rubberbuf.util which uses cljs-node-io.core that is not available to cljs browser runtime
            [rubberbuf.ast-postprocess :refer [unnest]]
            ))

; same as clojobuf.core/protoc, but defined separately help cljs browser runtime avoid dependency on cljs-node-io.core
(defn protoc
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
    [codec malli]))