(ns clojobuf.example.ex3
  (:require [clojobuf.core :refer [encode decode find-fault ->malli-registry]]
            [clojobuf.macro :refer [protoc-macro]]))

; use protoc-macro so that schemas is generated at compile time, which is especially useful for cljs which cannot
; use protoc at runtime as js doesn't have file system access.
(def registry (let [[codec malli] (protoc-macro ["resources/protobuf/"] ["example.proto"])]
                [codec (->malli-registry malli)]))


;-------------------------------------------------------------------
; Code below are identical to ex1.cljc
;-------------------------------------------------------------------

; message to be encoded
(def msg {:int32_val -1,
          :string_val "abc",
          :bool_val false,
          :enum_val :ZERO,
          :either :sint32_val,
          :sint32_val -1
          :int64_string {1 "abc", 2 "def"}
          :double_vals [0.0, 1.0, 2.0]})

; message to be encoded
(def msg2 {:msg1 msg, :msg1s [msg, msg]})

;-------------------------------------------------------------------
; Success case
;-------------------------------------------------------------------
(def binary (encode registry :my.pb.ns/Msg msg))
(decode registry :my.pb.ns/Msg binary)
; => msg

(def binary2 (encode registry :my.pb.ns/Msg2 msg2))
(decode registry :my.pb.ns/Msg2 binary2)
; => msg2

;-------------------------------------------------------------------
; Error case
;-------------------------------------------------------------------
(let [bin (encode registry :my.pb.ns/Msg {:this-field-doesnt-exists 0})]
  (when (nil? bin)
    (find-fault registry :my.pb.ns/Msg {:this-field-doesnt-exists 0})))
; => {:this-field-doesnt-exists ["disallowed key"]}
