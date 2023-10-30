(ns clojobuf.macro
  (:require [clojobuf.core :refer [protoc]]))

(defmacro protoc-macro
  [paths files]
  (protoc paths files :auto-malli-registry false))
