(ns clojobuf.test-core
  (:require [clojobuf.core :refer [encode decode protoc find-fault]]
            [clojure.test :refer [is deftest run-tests]]
            [malli.core :as m]))

(def registry (protoc ["resources/protobuf/"] ["nested.proto", "no_package.proto", "extension.proto"]))

(defn codec [msg-id msg]
  (->> msg
       (encode registry msg-id)
       (decode registry msg-id)))

(m/validate [:ref :my.ns.map/Mappy] {:a :b} (second registry))

(defmacro rt [msg-id msg]
  `(is (= (codec ~msg-id ~msg) ~msg)))

(deftest test-codec-mappy
  (rt :my.ns.map/Mappy {:uint32_sint64 {0 0, 1 1, 2 -2, 3 3, 4 -4}})
  (rt :my.ns.map/Mappy {:int64_string {0 "", 1 "abc", -2 "def", 3 "hij", -4 "klm"}})
  (rt :my.ns.map/Mappy {:fixed32_double {0 0.0, 1000 -1.0, 1001 0.0, 1002 1.0}})
  (rt :my.ns.map/Mappy {:sfixed64_enum {-1 :MINUS_ONE, 0 :ZERO, 1 :ONE}})
  (rt :my.ns.map/Mappy {:sint64_singular {-1 {:int32_val -1}}})
  (rt :my.ns.map/Mappy {:uint64_packed {1 {:sint32_val [-2 -1 0 1 2]}}}))

(deftest test-codec-oneof
  (rt :my.ns.oneof/Either {:either :int32_val, :int32_val 0})
  (rt :my.ns.oneof/Either {:either :int64_val, :int64_val 0})
  (rt :my.ns.oneof/Either {:either :uint32_val, :uint32_val 0})
  (rt :my.ns.oneof/Either {:either :uint64_val, :uint64_val 0})
  (rt :my.ns.oneof/Either {:either :sint32_val, :sint32_val 0})
  (rt :my.ns.oneof/Either {:either :sint64_val, :sint64_val 0})
  (rt :my.ns.oneof/Either {:either :bool_val, :bool_val false})
  (rt :my.ns.oneof/Either {:either :enum_val, :enum_val :ZERO})
  (rt :my.ns.oneof/Either {:either :fixed64_val, :fixed64_val 0})
  (rt :my.ns.oneof/Either {:either :sfixed64_val, :sfixed64_val 0})
  (rt :my.ns.oneof/Either {:either :double_val, :double_val 0.0})
  (rt :my.ns.oneof/Either {:either :string_val, :string_val ""})
  (rt :my.ns.oneof/Either {:either :fixed32_val, :fixed32_val 0})
  (rt :my.ns.oneof/Either {:either :sfixed32_val, :sfixed32_val 0})
  (rt :my.ns.oneof/Either {:either :float_val, :float_val 0.0})
  (rt :my.ns.oneof/Either {:either :singular_msg, :singular_msg {}})
  (rt :my.ns.oneof/Either {:either :packed_msg, :packed_msg {}}))

(deftest test-codec-singular
  (rt :my.ns.singular/Singular {:int32_val    -1})
  (rt :my.ns.singular/Singular {:int64_val    -1})
  (rt :my.ns.singular/Singular {:uint64_val    1})
  (rt :my.ns.singular/Singular {:uint32_val    1})
  (rt :my.ns.singular/Singular {:sint64_val   -1})
  (rt :my.ns.singular/Singular {:sint32_val   -1})
  (rt :my.ns.singular/Singular {:bool_val  false})
  (rt :my.ns.singular/Singular {:bool_val   true})
  (rt :my.ns.singular/Singular {:enum_val  :ZERO})
  (rt :my.ns.singular/Singular {:fixed64_val   1})
  (rt :my.ns.singular/Singular {:sfixed64_val -1})
  (rt :my.ns.singular/Singular {:double_val -1.0})
  (rt :my.ns.singular/Singular {:string_val "abc"})
  (rt :my.ns.singular/Singular {:fixed32_val   1})
  (rt :my.ns.singular/Singular {:sfixed32_val -1})
  (rt :my.ns.singular/Singular {:float_val  -1.0})
  (rt :my.ns.singular/Singular {:int32_val    -1
                                :int64_val    -1
                                :uint64_val    1
                                :uint32_val    1
                                :sint64_val   -1
                                :sint32_val   -1
                                :bool_val  false
                                :enum_val  :ZERO
                                :fixed64_val   1
                                :sfixed64_val -1
                                :double_val -1.0
                                :string_val "abc"
                                :fixed32_val  1
                                :sfixed32_val -1
                                :float_val  -1.0}))

(deftest test-codec-packed
  (rt :my.ns.packed/Packed {:int32_val    [-1 0 1]})
  (rt :my.ns.packed/Packed {:int64_val    [-1 0 1]})
  (rt :my.ns.packed/Packed {:uint64_val    [1 2 3]})
  (rt :my.ns.packed/Packed {:uint32_val    [1 2 3]})
  (rt :my.ns.packed/Packed {:sint64_val   [-1 0 1]})
  (rt :my.ns.packed/Packed {:sint32_val   [-1 0 1]})
  (rt :my.ns.packed/Packed {:bool_val  [false true]})
  (rt :my.ns.packed/Packed {:enum_val  [:MINUS_ONE :ZERO :ONE]})
  (rt :my.ns.packed/Packed {:fixed64_val   [1 2 3]})
  (rt :my.ns.packed/Packed {:sfixed64_val [-1 0 1]})
  (rt :my.ns.packed/Packed {:double_val [-1.0 0.0 1.0]})
  (rt :my.ns.packed/Packed {:string_val ["abc" "def" "ghi"]})
  (rt :my.ns.packed/Packed {:fixed32_val   [1 2 3]})
  (rt :my.ns.packed/Packed {:sfixed32_val [-1 0 1]})
  (rt :my.ns.packed/Packed {:float_val  [-1.0 0.0 1.0]})
  (rt :my.ns.packed/Packed {:int32_val    [-1 0 1]
                            :int64_val    [-1 0 1]
                            :uint64_val    [1 2 3]
                            :uint32_val    [1 2 3]
                            :sint64_val   [-1 0 1]
                            :sint32_val   [-1 0 1]
                            :bool_val  [false true]
                            :enum_val  [:MINUS_ONE :ZERO :ONE]
                            :fixed64_val   [1 2 3]
                            :sfixed64_val [-1 0 1]
                            :double_val [-1.0 0.0 1.0]
                            :string_val ["abc" "def" "ghi"]
                            :fixed32_val  [1 2 3]
                            :sfixed32_val [-1 0 1]
                            :float_val  [-1.0 0.0 1.0]}))

(deftest test-codec-repeat
  (rt :my.ns.repeat/Repeat {:int32_val    [-1 0 1]})
  (rt :my.ns.repeat/Repeat {:int64_val    [-1 0 1]})
  (rt :my.ns.repeat/Repeat {:uint64_val    [1 2 3]})
  (rt :my.ns.repeat/Repeat {:uint32_val    [1 2 3]})
  (rt :my.ns.repeat/Repeat {:sint64_val   [-1 0 1]})
  (rt :my.ns.repeat/Repeat {:sint32_val   [-1 0 1]})
  (rt :my.ns.repeat/Repeat {:bool_val  [false true]})
  (rt :my.ns.repeat/Repeat {:enum_val  [:MINUS_ONE :ZERO :ONE]})
  (rt :my.ns.repeat/Repeat {:fixed64_val   [1 2 3]})
  (rt :my.ns.repeat/Repeat {:sfixed64_val [-1 0 1]})
  (rt :my.ns.repeat/Repeat {:double_val [-1.0 0.0 1.0]})
  (rt :my.ns.repeat/Repeat {:string_val ["abc" "def" "ghi"]})
  (rt :my.ns.repeat/Repeat {:fixed32_val   [1 2 3]})
  (rt :my.ns.repeat/Repeat {:sfixed32_val [-1 0 1]})
  (rt :my.ns.repeat/Repeat {:float_val  [-1.0 0.0 1.0]})
  (rt :my.ns.repeat/Repeat {:int32_val    [-1 0 1]
                            :int64_val    [-1 0 1]
                            :uint64_val    [1 2 3]
                            :uint32_val    [1 2 3]
                            :sint64_val   [-1 0 1]
                            :sint32_val   [-1 0 1]
                            :bool_val  [false true]
                            :enum_val  [:MINUS_ONE :ZERO :ONE]
                            :fixed64_val   [1 2 3]
                            :sfixed64_val [-1 0 1]
                            :double_val [-1.0 0.0 1.0]
                            :string_val ["abc" "def" "ghi"]
                            :fixed32_val  [1 2 3]
                            :sfixed32_val [-1 0 1]
                            :float_val  [-1.0 0.0 1.0]}))

(deftest test-codec-nested
  (rt :my.ns.nested/Msg1 {:enum :ZERO
                          :nested2
                          {:nested3
                           {:nested4
                            {:nested5
                             {:either {:either :int32_val, :int32_val 1}}}}}
                          :nested3
                          {:nested4
                           {:nested5
                            {:either {:either :int32_val, :int32_val 1}}}}
                          :nested4
                          {:nested5
                           {:either {:either :int32_val, :int32_val 1}}}
                          :nested5
                          {:either {:either :int32_val, :int32_val 1}}}))

(deftest test-codec-no-package
  (rt :Msg1 {:int32_val 100})
  (rt :MsgA {:int32_val 100})
  (rt :MsgA.MsgB {:int64_val 100})
  (rt :MsgA.MsgB.MsgC {:uint32_val 100}))

(deftest test-find-fault
  (is (= (find-fault registry :Msg1 {:a :b}) {:a ["disallowed key"]}))
  (is (= (find-fault registry :my.ns.nested/Msg1 {:a :b}) {:a ["disallowed key"]})))

(deftest test-extension
  (rt :my.ns.extension/Extendable {:int32_val 1, :int64_val 2})
  (rt :my.ns.extension/Extendable {:my.ns.extension/Msg1.double_val 1.0})
  (rt :my.ns.extension/Extendable {:my.ns.extension/string_val "abcd"}))

(run-tests)
