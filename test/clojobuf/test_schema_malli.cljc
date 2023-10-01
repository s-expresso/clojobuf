(ns clojobuf.test-schema-malli
  (:require [clojobuf.core :refer [protoc]]
            [clojure.test :refer [is deftest run-tests]]
            [malli.core :as m]
            [malli.registry :as mr]))

(def codec_malli (protoc ["resources/protobuf/"] ["nested.proto"]
                         :malli-composite-registry false))
(def malli-schema (second codec_malli))
(def registry {:registry (mr/composite-registry
                          m/default-registry
                          malli-schema)})

(deftest test-schema-malli-enum
  (is (= (malli-schema :my.ns.enum/Enum)
         [:enum :MINUS_ONE :ZERO :ONE :TWO :THREE :FOUR :FIVE]))
  (is (= (malli-schema :my.ns.enum/EnumV)
         [:enum :DEFAULT :V1 :V2 :V3 :V1000]))
  (is (true?  (m/validate [:ref :my.ns.enum/Enum] :ZERO registry)))
  (is (false? (m/validate [:ref :my.ns.enum/Enum] :SIX registry)))
  (is (true?  (m/validate [:ref :my.ns.enum/EnumV] :V1000 registry)))
  (is (false? (m/validate [:ref :my.ns.enum/EnumV] :V1001 registry))))

(deftest test-schema-malli-map
  (is (= (malli-schema :my.ns.map/Mappy)
         [:map
          {:closed true}
          [:uint32_sint64 {:optional true} [:map-of :int :int]]
          [:int64_string {:optional true} [:map-of :int :string]]
          [:fixed32_double {:optional true} [:map-of :int :double]]
          [:sfixed64_enum {:optional true} [:map-of :int [:ref :my.ns.enum/Enum]]]
          [:sint64_singular {:optional true} [:map-of :int [:ref :my.ns.singular/Singular]]]
          [:uint64_packed {:optional true} [:map-of :int [:ref :my.ns.packed/Packed]]]]))
  (is (true?  (m/validate [:ref :my.ns.map/Mappy] {:uint32_sint64 {0 0, 1 -1, 2 2, 3 -3}} registry)))
  (is (false? (m/validate [:ref :my.ns.map/Mappy] {:uint32_sint64 {1 1.234}} registry)))
  (is (false? (m/validate [:ref :my.ns.map/Mappy] {:uint32_sint64 {1.234 0}} registry)))
  (is (false? (m/validate [:ref :my.ns.map/Mappy] {:a :b} registry))))

(deftest test-schema-malli-nested
  (is (= (malli-schema :my.ns.nested/Msg1)
         [:map
          {:closed true}
          [:enum {:optional true} [:ref :my.ns.enum/Enum]]
          [:nested2 {:optional true} [:ref :my.ns.nested/Msg1.Msg2]]
          [:nested3 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3]]
          [:nested4 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4]]
          [:nested5 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]]))
  (is (= (malli-schema :my.ns.nested/Msg1.Msg2)
         [:map
          {:closed true}
          [:singular {:optional true} [:ref :my.ns.singular/Singular]]
          [:nested3 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3]]
          [:nested4 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4]]
          [:nested5 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]]))
  (is (= (malli-schema :my.ns.nested/Msg1.Msg2.Msg3)
         [:map
          {:closed true}
          [:packed {:optional true} [:ref :my.ns.packed/Packed]]
          [:repeat {:optional true} [:ref :my.ns.repeat/Repeat]]
          [:nested4 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4]]
          [:nested5 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]]))
  (is (= (malli-schema :my.ns.nested/Msg1.Msg2.Msg3.Msg4)
         [:map
          {:closed true}
          [:mappy {:optional true} [:ref :my.ns.map/Mappy]]
          [:nested5 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]]))
  (is (= (malli-schema :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5)
         [:map {:closed true} [:either {:optional true} [:ref :my.ns.oneof/Either]]])))

; TODO compare dynamic function at end of form (currently bypassed with drop-last)
(deftest test-schema-malli-oneof
  (is (= (drop-last (malli-schema :my.ns.oneof/Either))
         [:and
          [:map
           {:closed true}
           [:either
            {:optional true}
            [:enum
             :int32_val
             :int64_val
             :uint32_val
             :uint64_val
             :sint32_val
             :sint64_val
             :bool_val
             :enum_val
             :fixed64_val
             :sfixed64_val
             :double_val
             :string_val
             :bytes_val
             :fixed32_val
             :sfixed32_val
             :float_val
             :singular_msg
             :packed_msg]]
           [:int32_val {:optional true} 'int?]
           [:int64_val {:optional true} 'int?]
           [:uint32_val {:optional true} 'int?]
           [:uint64_val {:optional true} 'int?]
           [:sint32_val {:optional true} 'int?]
           [:sint64_val {:optional true} 'int?]
           [:bool_val {:optional true} 'boolean?]
           [:enum_val {:optional true} [:ref :my.ns.enum/Enum]]
           [:fixed64_val {:optional true} 'int?]
           [:sfixed64_val {:optional true} 'int?]
           [:double_val {:optional true} 'double?]
           [:string_val {:optional true} 'string?]
           [:bytes_val {:optional true} 'bytes?]
           [:fixed32_val {:optional true} 'int?]
           [:sfixed32_val {:optional true} 'int?]
           [:float_val {:optional true} 'float?]
           [:singular_msg {:optional true} [:ref :my.ns.singular/Singular]]
           [:packed_msg {:optional true} [:ref :my.ns.packed/Packed]]]
          ; TODO compare dynamic function at end of form (currently bypassed with drop-last)
          ]))
  (is (true?  (m/validate [:ref :my.ns.oneof/Either] {:either :string_val, :string_val "abc"} registry)))
  (is (true?  (m/validate [:ref :my.ns.oneof/Either] {:string_val "abc"} registry)))
  (is (false?  (m/validate [:ref :my.ns.oneof/Either] {:either :string_val} registry)))
  (is (false?  (m/validate [:ref :my.ns.oneof/Either] {:either :string_val, :uint32_val 1} registry)))
  (is (false?  (m/validate [:ref :my.ns.oneof/Either] {:a :b} registry))))

(deftest test-schema-malli-packed
  (is (= (malli-schema :my.ns.packed/Packed)
         [:map
          {:closed true}
          [:int32_val {:optional true} [:vector 'int?]]
          [:int64_val {:optional true} [:vector 'int?]]
          [:uint32_val {:optional true} [:vector 'int?]]
          [:uint64_val {:optional true} [:vector 'int?]]
          [:sint32_val {:optional true} [:vector 'int?]]
          [:sint64_val {:optional true} [:vector 'int?]]
          [:bool_val {:optional true} [:vector 'boolean?]]
          [:enum_val {:optional true} [:vector [:ref :my.ns.enum/Enum]]]
          [:fixed64_val {:optional true} [:vector 'int?]]
          [:sfixed64_val {:optional true} [:vector 'int?]]
          [:double_val {:optional true} [:vector 'double?]]
          [:string_val {:optional true} [:vector 'string?]]
          [:bytes_val {:optional true} [:vector 'bytes?]]
          [:fixed32_val {:optional true} [:vector 'int?]]
          [:sfixed32_val {:optional true} [:vector 'int?]]
          [:float_val {:optional true} [:vector 'float?]]
          [:singular_msg {:optional true} [:vector [:ref :my.ns.singular/Singular]]]]))
  (is (true?  (m/validate [:ref :my.ns.packed/Packed] {:int32_val [0 1 2 3]} registry)))
  (is (false? (m/validate [:ref :my.ns.packed/Packed] {:a :b} registry))))

(deftest test-schema-malli-repeat
  (is (= (malli-schema :my.ns.repeat/Repeat)
         [:map
          {:closed true}
          [:int32_val {:optional true} [:vector 'int?]]
          [:int64_val {:optional true} [:vector 'int?]]
          [:uint32_val {:optional true} [:vector 'int?]]
          [:uint64_val {:optional true} [:vector 'int?]]
          [:sint32_val {:optional true} [:vector 'int?]]
          [:sint64_val {:optional true} [:vector 'int?]]
          [:bool_val {:optional true} [:vector 'boolean?]]
          [:enum_val {:optional true} [:vector [:ref :my.ns.enum/Enum]]]
          [:fixed64_val {:optional true} [:vector 'int?]]
          [:sfixed64_val {:optional true} [:vector 'int?]]
          [:double_val {:optional true} [:vector 'double?]]
          [:string_val {:optional true} [:vector 'string?]]
          [:bytes_val {:optional true} [:vector 'bytes?]]
          [:fixed32_val {:optional true} [:vector 'int?]]
          [:sfixed32_val {:optional true} [:vector 'int?]]
          [:float_val {:optional true} [:vector 'float?]]
          [:singular_msg {:optional true} [:vector [:ref :my.ns.singular/Singular]]]]))
  (is (true?  (m/validate [:ref :my.ns.repeat/Repeat] {:int32_val [0 1 2 3]} registry)))
  (is (false? (m/validate [:ref :my.ns.repeat/Repeat] {:int32_val [0.0]} registry)))
  (is (false? (m/validate [:ref :my.ns.repeat/Repeat] {:a :b} registry))))

(deftest test-schema-malli-singular
  (is (= (malli-schema :my.ns.singular/Singular)
         [:map
          {:closed true}
          [:int32_val {:optional true} 'int?]
          [:int64_val {:optional true} 'int?]
          [:uint32_val {:optional true} 'int?]
          [:uint64_val {:optional true} 'int?]
          [:sint32_val {:optional true} 'int?]
          [:sint64_val {:optional true} 'int?]
          [:bool_val {:optional true} 'boolean?]
          [:enum_val {:optional true} [:ref :my.ns.enum/Enum]]
          [:fixed64_val {:optional true} 'int?]
          [:sfixed64_val {:optional true} 'int?]
          [:double_val {:optional true} 'double?]
          [:string_val {:optional true} 'string?]
          [:bytes_val {:optional true} 'bytes?]
          [:fixed32_val {:optional true} 'int?]
          [:sfixed32_val {:optional true} 'int?]
          [:float_val {:optional true} 'float?]]))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:int32_val 0} registry)))
  (is (false? (m/validate [:ref :my.ns.singular/Singular] {:int32_val 0.0} registry)))
  (is (false? (m/validate [:ref :my.ns.singular/Singular] {:a :b} registry))))

(run-tests)