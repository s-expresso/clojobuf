(ns clojobuf.util
  (:require [malli.core :as m][malli.transform :as mt]))

(defn fid           [field-def] (nth field-def 0))
(defn fname         [field-def] (nth field-def 0))
(defn typ           [field-def] (nth field-def 1))
(defn rori          [field-def] (nth field-def 2))
(defn ktype-vtype   [field-def] (nth field-def 2))
(defn options       [field-def] (nth field-def 3))

(defn msg|enum?     [field-def] (string? (typ field-def)))
(defn map??         [field-def] (= (typ field-def) :map))
(defn msg|enum-id   [field-def] (keyword (typ field-def)))

(defn repeated?     [field-def] (= (rori field-def) :repeated))
(defn proto3? [msg-def|enum-def] (= :proto3 (msg-def|enum-def :syntax)))

(defn packed?
  [msg-def|enum-def|field-def] (if (map? msg-def|enum-def|field-def) ; msg/enum
                                 (proto3? msg-def|enum-def|field-def)
                                 (->> msg-def|enum-def|field-def ; field
                                      options
                                      (some #(= % ["packed" true]))
                                      true?)))
(defn packable? [typ] (and (keyword? typ) (not= typ :bytes) (not= typ :string)))

(defn default-opt   [options] (->> options (some #(when (= (first %) "default") %)) second))

(defn default-enum-val
  "Use field's default option if available, else first enum val in enum-def is default."
  [enum-def field-def]
  (or (default-opt (options field-def)) (enum-def :default)))

(defn default-pri
  "Get default value of primitive type; will return 0 if pri-type = :bytes."
  [pri-type]
  (condp = pri-type
    :bool false
    :string ""
    0)) ; all other pri-types are numeric

(defn raise [err-txt]
  #?(:clj (throw (Exception. err-txt)))
  #?(:cljs (throw (js/Error err-txt))))

(defn dot-qualify
  "Malli schema requires fully qualified keyword as ref, hence for a protobuf message Msg1 without package
   we use :./Msg1 *internally* to make it fully qualified."
  [kw]
  (if (qualified-keyword? kw) kw (->> kw name (str "./") keyword)))

(defn fill-default
  ([registry msg-id]
   (fill-default registry msg-id {}))
  ([registry msg-id msg]
   (let [vt (mt/default-value-transformer
             {; ::mt/add-optional-keys true
              :defaults {:string (constantly "")
                         :boolean (constantly false)
                         :int (constantly 0)
                         :double (constantly 0.0)
                         :ref (constantly {})
                         }
              ;; :default-fn (fn [k v]
              ;;               (do (println "k is " k)
              ;;                   (println "v is " v)
              ;;                   (println "---------------------------------")
              ;;                   (case k
              ;;                     :string ""
              ;;                     :boolean false
              ;;                     :int 0
              ;;                     :double 0
              ;;                     :ref (m/decode [:ref v] {} (second registry) vt)
              ;;                     nil)))
              })]
     (m/decode [:ref msg-id]
               msg
               (second registry)
               vt))))