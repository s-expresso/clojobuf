(ns clojobuf.test-util
  (:require [clojobuf.util :refer [default-opt default-enum-val]]
            [clojure.test :refer [is deftest run-tests]]))

(deftest test-default-opt
  (is (= (default-opt nil) nil))
  (is (= (default-opt []) nil))
  (is (= (default-opt [["dummy" 0]]) nil))
  (is (= (default-opt [["dummy" 0] ["default" 0]]) 0))
  (is (= (default-opt [["dummy" 0] ["default" 1]]) 1))
  (is (= (default-opt [["dummy" 0] ["default" true]]) true))
  (is (= (default-opt [["dummy" 0] ["default" false]]) false))
  (is (= (default-opt [["dummy" 0] ["default" ""]]) ""))
  (is (= (default-opt [["dummy" 0] ["default" "abc"]]) "abc"))
  (is (= (default-opt [["dummy" 0] ["default" 0.0]]) 0.0))
  (is (= (default-opt [["dummy" 0] ["default" 1.0]]) 1.0))
  (is (= (default-opt [["dummy" 0] ["default" -1.0]]) -1.0))
  (is (= (default-opt [["dummy" 0] ["default" nil]]) nil))
  ; should choose the first default
  (is (= (default-opt [["default" 1] ["default" 2]]) 1)))

(def enum-schema {:syntax :proto2,
                  :type :enum,
                  :default :MINUS_ONE,
                  :encode {:MINUS_ONE -1, :ZERO 0, :ONE 1, :TWO 2, :THREE 3, :FOUR 4, :FIVE 5},
                  :decode {-1 :MINUS_ONE, 0 :ZERO, 1 :ONE, 2 :TWO, 3 :THREE, 4 :FOUR, 5 :FIVE}})

(deftest test-default-enum-val
  (is (= (default-enum-val
          enum-schema [8 "enum.Enum" :optional nil]) :MINUS_ONE))
  (is (= (default-enum-val
          enum-schema [8 "enum.Enum" :optional [["dummy" :ONE]]]) :MINUS_ONE))
  (is (= (default-enum-val
          enum-schema [8 "enum.Enum" :optional [["dummy" :ONE] ["default" :ONE]]]) :ONE)))

(run-tests)
