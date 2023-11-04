(ns clojobuf.example.ex2
  (:require #?(:clj  [clojobuf.core :refer [encode decode find-fault ->malli-registry protoc]]
               :cljs [clojobuf.core :refer [encode decode find-fault ->malli-registry]])
            #?(:cljs [clojobuf.nodejs :refer [protoc]])))

; use protoc with :auto-malli-registry false
(def schemas (protoc ["resources/protobuf/"] ["example.proto"] :auto-malli-registry false))

#?(:clj (clojure.pprint/pprint schemas)
   :cljs (cljs.pprint/pprint schemas))
;; => [#:my.pb.ns{:Enum
;;                {:syntax :proto2,
;;                :type :enum,
;;                :default :MINUS_ONE,
;;                :encode {:MINUS_ONE -1, :ZERO 0, :ONE 1},
;;                :decode {-1 :MINUS_ONE, 0 :ZERO, 1 :ONE}},
;;                ...}
;;     #:my.pb.ns{:Enum [:enum :MINUS_ONE :ZERO :ONE],
;;                ...}]

; equivalent to (def registry (protoc ["resources/protobuf/"] ["example.proto"]))
(def registry [(first schemas)
               (->malli-registry (second schemas))])

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
