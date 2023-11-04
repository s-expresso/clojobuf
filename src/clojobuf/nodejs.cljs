(ns clojobuf.nodejs
  (:require [clojobuf.core :refer [->malli-registry]]
            [clojobuf.schema :refer [xform-ast]]
            [rubberbuf.core :as rc] ; rubberbuf.core uses rubberbuf.util which uses cljs-node-io.core that is not available to cljs browser runtime
            [rubberbuf.ast-postprocess :refer [unnest]]
            ))

; same as clojobuf.core/protoc, but defined separately help cljs browser runtime avoid dependency on cljs-node-io.core
(defn protoc
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
    [codec malli]))