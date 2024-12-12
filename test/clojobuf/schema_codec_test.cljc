(ns clojobuf.schema-codec-test
  (:require #?(:clj [clojobuf.core :refer [protoc]]
               :cljs [clojobuf.nodejs :refer [protoc]])
            [clojure.test :refer [is deftest run-tests]]))

(def codec_malli (protoc ["resources/protobuf/"] ["nested.proto",
                                                  "no_package.proto", 
                                                  "extension.proto", 
                                                  "required.proto", 
                                                  "implicit.proto"]))
(def codec-schema (first codec_malli))

(deftest test-schema-codec-enum
  (is (= (codec-schema :my.ns.enum/Enum)
         {:syntax :proto2,
          :type :enum,
          :default :MINUS_ONE,
          :encode {:MINUS_ONE -1, :ZERO 0, :ONE 1, :TWO 2, :THREE 3, :FOUR 4, :FIVE 5},
          :decode {-1 :MINUS_ONE, 0 :ZERO, 1 :ONE, 2 :TWO, 3 :THREE, 4 :FOUR, 5 :FIVE}}))
  (is (= (codec-schema :my.ns.enum/EnumV)
         {:syntax :proto2,
          :type :enum,
          :default :DEFAULT,
          :encode {:DEFAULT 0, :V1 1, :V2 2, :V3 3, :V1000 1000},
          :decode {0 :DEFAULT, 1 :V1, 2 :V2, 3 :V3, 1000 :V1000}})))

(deftest test-schema-codec-map
  (is (= (codec-schema :my.ns.map/Mappy)
         {:syntax :proto3,
          :type :msg,
          :encode
          {:uint32_sint64 [1 :map [:uint32 :sint64] nil],
           :int64_string [2 :map [:int64 :string] nil],
           :fixed32_double [3 :map [:fixed32 :double] nil],
           :sfixed64_enum [4 :map [:sfixed64 "my.ns.enum/Enum"] nil],
           :sint64_singular [5 :map [:sint64 "my.ns.singular/Singular"] nil],
           :uint64_packed [6 :map [:uint64 "my.ns.packed/Packed"] nil]},
          :decode
          {1 [:uint32_sint64 :map [:uint32 :sint64] nil],
           2 [:int64_string :map [:int64 :string] nil],
           3 [:fixed32_double :map [:fixed32 :double] nil],
           4 [:sfixed64_enum :map [:sfixed64 "my.ns.enum/Enum"] nil],
           5 [:sint64_singular :map [:sint64 "my.ns.singular/Singular"] nil],
           6 [:uint64_packed :map [:uint64 "my.ns.packed/Packed"] nil]}})))

(deftest test-schema-codec-nested
  (is (= (codec-schema :my.ns.nested/Msg1)
         {:syntax :proto3,
          :type :msg,
          :encode
          {:enum [1 "my.ns.enum/Enum" nil nil],
           :nested2 [2 "my.ns.nested/Msg1.Msg2" nil nil],
           :nested3 [3 "my.ns.nested/Msg1.Msg2.Msg3" nil nil],
           :nested4 [4 "my.ns.nested/Msg1.Msg2.Msg3.Msg4" nil nil],
           :nested5 [5 "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]
           :nested5a [6 "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]
           :nested5b [7 "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]
           :nested5c [8 "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]},
          :decode
          {1 [:enum "my.ns.enum/Enum" nil nil],
           2 [:nested2 "my.ns.nested/Msg1.Msg2" nil nil],
           3 [:nested3 "my.ns.nested/Msg1.Msg2.Msg3" nil nil],
           4 [:nested4 "my.ns.nested/Msg1.Msg2.Msg3.Msg4" nil nil],
           5 [:nested5 "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]
           6 [:nested5a "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]
           7 [:nested5b "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]
           8 [:nested5c "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]}}))
  (is (= (codec-schema :my.ns.nested/Msg1.Msg2)
         {:syntax :proto3,
          :type :msg,
          :encode
          {:singular [1 "my.ns.singular/Singular" nil nil],
           :nested3 [3 "my.ns.nested/Msg1.Msg2.Msg3" nil nil],
           :nested4 [4 "my.ns.nested/Msg1.Msg2.Msg3.Msg4" nil nil],
           :nested5 [5 "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]},
          :decode
          {1 [:singular "my.ns.singular/Singular" nil nil],
           3 [:nested3 "my.ns.nested/Msg1.Msg2.Msg3" nil nil],
           4 [:nested4 "my.ns.nested/Msg1.Msg2.Msg3.Msg4" nil nil],
           5 [:nested5 "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]}}))
  (is (= (codec-schema :my.ns.nested/Msg1.Msg2.Msg3)
         {:syntax :proto3,
          :type :msg,
          :encode
          {:packed [1 "my.ns.packed/Packed" nil nil],
           :repeat [2 "my.ns.repeat/Repeat" nil nil],
           :nested4 [4 "my.ns.nested/Msg1.Msg2.Msg3.Msg4" nil nil],
           :nested5 [5 "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]},
          :decode
          {1 [:packed "my.ns.packed/Packed" nil nil],
           2 [:repeat "my.ns.repeat/Repeat" nil nil],
           4 [:nested4 "my.ns.nested/Msg1.Msg2.Msg3.Msg4" nil nil],
           5 [:nested5 "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]}}))
  (is (= (codec-schema :my.ns.nested/Msg1.Msg2.Msg3.Msg4)
         {:syntax :proto3,
          :type :msg,
          :encode {:mappy [1 "my.ns.map/Mappy" nil nil],
                   :nested5 [5 "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]},
          :decode {1 [:mappy "my.ns.map/Mappy" nil nil],
                   5 [:nested5 "my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5" nil nil]}}))
  (is (= (codec-schema :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5)
         {:syntax :proto3,
          :type :msg,
          :encode {:either [1 "my.ns.oneof/Either" nil nil]},
          :decode {1 [:either "my.ns.oneof/Either" nil nil]}})))

(deftest test-schema-codec-oneof
  (is (= (codec-schema :my.ns.oneof/Either)
         {:syntax :proto3,
          :type :msg,
          :encode
          {:string_val [12 :string :oneof nil],
           :fixed32_val [14 :fixed32 :oneof nil],
           :sint32_val [5 :sint32 :oneof nil],
           :sfixed64_val [10 :sfixed64 :oneof nil],
           :singular_msg [17 "my.ns.singular/Singular" :oneof nil],
           :bytes_val [13 :bytes :oneof nil],
           :uint32_val [3 :uint32 :oneof nil],
           :packed_msg [18 "my.ns.packed/Packed" :oneof nil],
           :sint64_val [6 :sint64 :oneof nil],
           :bool_val [7 :bool :oneof nil],
           :uint64_val [4 :uint64 :oneof nil],
           :int64_val [2 :int64 :oneof nil],
           :fixed64_val [9 :fixed64 :oneof nil],
           :float_val [16 :float :oneof nil],
           :enum_val [8 "my.ns.enum/Enum" :oneof nil],
           :double_val [11 :double :oneof nil],
           :int32_val [1 :int32 :oneof nil],
           :sfixed32_val [15 :sfixed32 :oneof nil],
           :msg_1 [19 "Msg1" :oneof nil],
           :msg_a [20 "MsgA" :oneof nil],
           :either
           [:oneof
            :string_val
            :fixed32_val
            :msg_1
            :sint32_val
            :sfixed64_val
            :singular_msg
            :bytes_val
            :uint32_val
            :packed_msg
            :sint64_val
            :bool_val
            :uint64_val
            :int64_val
            :fixed64_val
            :float_val
            :enum_val
            :double_val
            :int32_val
            :msg_a
            :sfixed32_val]}
          :decode
          {7 [:bool_val :bool [:oneof :either] nil],
           1 [:int32_val :int32 [:oneof :either] nil],
           4 [:uint64_val :uint64 [:oneof :either] nil],
           15 [:sfixed32_val :sfixed32 [:oneof :either] nil],
           13 [:bytes_val :bytes [:oneof :either] nil],
           6 [:sint64_val :sint64 [:oneof :either] nil],
           17 [:singular_msg "my.ns.singular/Singular" [:oneof :either] nil],
           3 [:uint32_val :uint32 [:oneof :either] nil],
           12 [:string_val :string [:oneof :either] nil],
           2 [:int64_val :int64 [:oneof :either] nil],
           11 [:double_val :double [:oneof :either] nil],
           9 [:fixed64_val :fixed64 [:oneof :either] nil],
           5 [:sint32_val :sint32 [:oneof :either] nil],
           14 [:fixed32_val :fixed32 [:oneof :either] nil],
           16 [:float_val :float [:oneof :either] nil],
           10 [:sfixed64_val :sfixed64 [:oneof :either] nil],
           18 [:packed_msg "my.ns.packed/Packed" [:oneof :either] nil],
           8 [:enum_val "my.ns.enum/Enum" [:oneof :either] nil],
           19 [:msg_1 "Msg1" [:oneof :either] nil],
           20 [:msg_a "MsgA" [:oneof :either] nil]}})))

(deftest test-schema-codec-packed
  (is (= (codec-schema :my.ns.packed/Packed)
         {:syntax :proto2,
          :type :msg,
          :encode
          {:string_val [12 :string :repeated nil],
           :fixed32_val [14 :fixed32 :repeated [["packed" :true]]],
           :sint32_val [5 :sint32 :repeated [["packed" :true]]],
           :sfixed64_val [10 :sfixed64 :repeated [["packed" :true]]],
           :singular_msg [17 "my.ns.singular/Singular" :repeated nil],
           :bytes_val [13 :bytes :repeated nil],
           :uint32_val [3 :uint32 :repeated [["packed" :true]]],
           :sint64_val [6 :sint64 :repeated [["packed" :true]]],
           :bool_val [7 :bool :repeated [["packed" :true]]],
           :uint64_val [4 :uint64 :repeated [["packed" :true]]],
           :int64_val [2 :int64 :repeated [["packed" :true]]],
           :fixed64_val [9 :fixed64 :repeated [["packed" :true]]],
           :float_val [16 :float :repeated [["packed" :true]]],
           :enum_val [8 "my.ns.enum/Enum" :repeated [["packed" :true]]],
           :double_val [11 :double :repeated [["packed" :true]]],
           :int32_val [1 :int32 :repeated [["packed" :true]]],
           :sfixed32_val [15 :sfixed32 :repeated [["packed" :true]]]},
          :decode
          {7 [:bool_val :bool :repeated [["packed" :true]]],
           1 [:int32_val :int32 :repeated [["packed" :true]]],
           4 [:uint64_val :uint64 :repeated [["packed" :true]]],
           15 [:sfixed32_val :sfixed32 :repeated [["packed" :true]]],
           13 [:bytes_val :bytes :repeated nil],
           6 [:sint64_val :sint64 :repeated [["packed" :true]]],
           17 [:singular_msg "my.ns.singular/Singular" :repeated nil],
           3 [:uint32_val :uint32 :repeated [["packed" :true]]],
           12 [:string_val :string :repeated nil],
           2 [:int64_val :int64 :repeated [["packed" :true]]],
           11 [:double_val :double :repeated [["packed" :true]]],
           9 [:fixed64_val :fixed64 :repeated [["packed" :true]]],
           5 [:sint32_val :sint32 :repeated [["packed" :true]]],
           14 [:fixed32_val :fixed32 :repeated [["packed" :true]]],
           16 [:float_val :float :repeated [["packed" :true]]],
           10 [:sfixed64_val :sfixed64 :repeated [["packed" :true]]],
           8 [:enum_val "my.ns.enum/Enum" :repeated [["packed" :true]]]}})))

(deftest test-schema-codec-repeat
  (is (= (codec-schema :my.ns.repeat/Repeat)
         {:syntax :proto2,
          :type :msg,
          :encode
          {:string_val [12 :string :repeated nil],
           :fixed32_val [14 :fixed32 :repeated nil],
           :sint32_val [5 :sint32 :repeated nil],
           :sfixed64_val [10 :sfixed64 :repeated nil],
           :singular_msg [17 "my.ns.singular/Singular" :repeated nil],
           :bytes_val [13 :bytes :repeated nil],
           :uint32_val [3 :uint32 :repeated nil],
           :sint64_val [6 :sint64 :repeated nil],
           :bool_val [7 :bool :repeated nil],
           :uint64_val [4 :uint64 :repeated nil],
           :int64_val [2 :int64 :repeated nil],
           :fixed64_val [9 :fixed64 :repeated nil],
           :float_val [16 :float :repeated nil],
           :enum_val [8 "my.ns.enum/Enum" :repeated nil],
           :double_val [11 :double :repeated nil],
           :int32_val [1 :int32 :repeated nil],
           :sfixed32_val [15 :sfixed32 :repeated nil]},
          :decode
          {7 [:bool_val :bool :repeated nil],
           1 [:int32_val :int32 :repeated nil],
           4 [:uint64_val :uint64 :repeated nil],
           15 [:sfixed32_val :sfixed32 :repeated nil],
           13 [:bytes_val :bytes :repeated nil],
           6 [:sint64_val :sint64 :repeated nil],
           17 [:singular_msg "my.ns.singular/Singular" :repeated nil],
           3 [:uint32_val :uint32 :repeated nil],
           12 [:string_val :string :repeated nil],
           2 [:int64_val :int64 :repeated nil],
           11 [:double_val :double :repeated nil],
           9 [:fixed64_val :fixed64 :repeated nil],
           5 [:sint32_val :sint32 :repeated nil],
           14 [:fixed32_val :fixed32 :repeated nil],
           16 [:float_val :float :repeated nil],
           10 [:sfixed64_val :sfixed64 :repeated nil],
           8 [:enum_val "my.ns.enum/Enum" :repeated nil]}})))

(deftest test-schema-codec-singular
  (is (= (codec-schema :my.ns.singular/Singular)
         {:syntax :proto2,
          :type :msg,
          :encode
          {:string_val [12 :string :optional nil],
           :fixed32_val [14 :fixed32 :optional nil],
           :sint32_val [5 :sint32 :optional nil],
           :sfixed64_val [10 :sfixed64 :optional nil],
           :bytes_val [13 :bytes :optional nil],
           :uint32_val [3 :uint32 :optional nil],
           :sint64_val [6 :sint64 :optional nil],
           :bool_val [7 :bool :optional nil],
           :uint64_val [4 :uint64 :optional nil],
           :int64_val [2 :int64 :optional nil],
           :fixed64_val [9 :fixed64 :optional nil],
           :float_val [16 :float :optional nil],
           :enum_val [8 "my.ns.enum/Enum" :optional nil],
           :double_val [11 :double :optional nil],
           :int32_val [1 :int32 :optional nil],
           :sfixed32_val [15 :sfixed32 :optional nil]},
          :decode
          {7 [:bool_val :bool :optional nil],
           1 [:int32_val :int32 :optional nil],
           4 [:uint64_val :uint64 :optional nil],
           15 [:sfixed32_val :sfixed32 :optional nil],
           13 [:bytes_val :bytes :optional nil],
           6 [:sint64_val :sint64 :optional nil],
           3 [:uint32_val :uint32 :optional nil],
           12 [:string_val :string :optional nil],
           2 [:int64_val :int64 :optional nil],
           11 [:double_val :double :optional nil],
           9 [:fixed64_val :fixed64 :optional nil],
           5 [:sint32_val :sint32 :optional nil],
           14 [:fixed32_val :fixed32 :optional nil],
           16 [:float_val :float :optional nil],
           10 [:sfixed64_val :sfixed64 :optional nil],
           8 [:enum_val "my.ns.enum/Enum" :optional nil]}})))

(deftest test-schema-codec-no-package
  (is (= (codec-schema :Msg1) {:syntax :proto2,
                               :type :msg,
                               :encode {:int32_val [1 :int32 :optional nil]},
                               :decode {1 [:int32_val :int32 :optional nil]}}))
  (is (= (codec-schema :MsgA) {:syntax :proto2,
                               :type :msg,
                               :encode {:int32_val [1 :int32 :optional nil]},
                               :decode {1 [:int32_val :int32 :optional nil]}}))
  (is (= (codec-schema :MsgA.MsgB) {:syntax :proto2,
                                    :type :msg,
                                    :encode {:int64_val [1 :int64 :optional nil]},
                                    :decode {1 [:int64_val :int64 :optional nil]}}))
  (is (= (codec-schema :MsgA.MsgB.MsgC) {:syntax :proto2,
                                         :type :msg,
                                         :encode {:uint32_val [1 :uint32 :optional nil]},
                                         :decode {1 [:uint32_val :uint32 :optional nil]}})))

(deftest test-extension
  (is (= (codec-schema :my.ns.extension/Extendable)
         {:syntax :proto2,
          :type :msg,
          :encode
          {:int32_val [1 :int32 :optional nil],
           :int64_val [2 :int64 :optional nil],
           :my.ns.extension/Msg1.double_val [100 :double :optional nil],
           :my.ns.extension/string_val [101 :string :optional nil]},
          :decode
          {1 [:int32_val :int32 :optional nil],
           2 [:int64_val :int64 :optional nil],
           100 [:my.ns.extension/Msg1.double_val :double :optional nil],
           101 [:my.ns.extension/string_val :string :optional nil]}})))

(deftest test-required
  (is (= (codec-schema :my.ns.required/Required)
         {:syntax :proto2,
          :type :msg,
          :encode
          {:string_val [12 :string :required nil],
           :fixed32_val [14 :fixed32 :required nil],
           :sint32_val [5 :sint32 :required nil],
           :sfixed64_val [10 :sfixed64 :required nil],
           :uint32_val [3 :uint32 :required nil],
           :sint64_val [6 :sint64 :required nil],
           :bool_val [7 :bool :required nil],
           :uint64_val [4 :uint64 :required nil],
           :int64_val [2 :int64 :required nil],
           :fixed64_val [9 :fixed64 :required nil],
           :float_val [16 :float :required nil],
           :enum_val [8 "my.ns.enum/Enum" :required nil],
           :double_val [11 :double :required nil],
           :int32_val [1 :int32 :required nil],
           :sfixed32_val [15 :sfixed32 :required nil]},
          :decode
          {7 [:bool_val :bool :required nil],
           1 [:int32_val :int32 :required nil],
           4 [:uint64_val :uint64 :required nil],
           15 [:sfixed32_val :sfixed32 :required nil],
           6 [:sint64_val :sint64 :required nil],
           3 [:uint32_val :uint32 :required nil],
           12 [:string_val :string :required nil],
           2 [:int64_val :int64 :required nil],
           11 [:double_val :double :required nil],
           9 [:fixed64_val :fixed64 :required nil],
           5 [:sint32_val :sint32 :required nil],
           14 [:fixed32_val :fixed32 :required nil],
           16 [:float_val :float :required nil],
           10 [:sfixed64_val :sfixed64 :required nil],
           8 [:enum_val "my.ns.enum/Enum" :required nil]}})))

(deftest test-implicit
  (is (= (codec-schema :my.ns.implicit/Implicit)
         {:syntax :proto3,
          :type :msg,
          :encode
          {:int32_val [1 :int32 nil nil],
           :string_val [2 :string nil nil]}
          :decode
          {1 [:int32_val :int32 nil nil],
           2 [:string_val :string nil nil]}}))
  (is (= (codec-schema :my.ns.implicit/Implicit2)
         {:syntax :proto3,
          :type :msg,
          :encode {:implicit [1 "my.ns.implicit/Implicit" nil nil]}
          :decode {1 [:implicit "my.ns.implicit/Implicit" nil nil]}})))
