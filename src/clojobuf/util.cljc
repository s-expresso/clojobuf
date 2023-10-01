(ns clojobuf.util)

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

(defn default-opt   [options] (->> options (some #(= (first %) "default")) #(when-not (nil? %) (second %))))

(defn default-enum-val
  "Use field's default option if available, else first enum val in enum-def is default."
  [enum-def field-def]
  (or (default-opt (options field-def)) (enum-def :default)))

(defn default-pri
  "Get default value of primitive type."
  [pri-type]
  (condp = pri-type
    :bool false
    :string ""
    :bytes (bytes (byte-array 0))
    0)) ; all other pri-types are numeric

