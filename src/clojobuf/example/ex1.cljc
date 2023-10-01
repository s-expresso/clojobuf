(ns clojobuf.example.ex1
  (:require [clojobuf.core :refer [encode decode gen-registries]]))

(def codec_malli (gen-registries ["resources/protobuf/"] ["example.proto"]))
; [<codec schemas> <malli schemas>] ; with the latter still WIP

(def codec-schemas (first codec_malli))

(def msg {:int32_val -1,
          :string_val "abc",
          :bool_val false,
          :enum_val :ZERO,
          :either :sint32_val,
          :sint32_val -1
          :int64_string {1 "abc", 2 "def"}
          :double_vals [0.0, 1.0, 2.0]})


(def binary (encode codec-schemas :my.pb.ns/Msg msg))
(decode codec-schemas :my.pb.ns/Msg binary)
; get back a map identical to msg


(def msg2 {:msg1 msg, :msg1s [msg, msg]})
(def binary2 (encode codec-schemas :my.pb.ns/Msg2 msg2))
(decode codec-schemas :my.pb.ns/Msg2 binary2)
; get back a map identical to msg2