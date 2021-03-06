;; small_or_large.clj
;; Tom Helmuth, thelmuth@cs.umass.edu
;;
;; Problem Source: iJava (http://ijava.cs.umass.edu/)
;;
;; Given an integer n in the range [-10000, 10000], print "small" if n < 1000
;; and "large" if n >= 2000 (and nothing if 1000 <= n < 2000).
;;
;; input stack has input integer n

(ns clojush.problems.software.small-or-large
  (:use clojush.pushgp.pushgp
        [clojush pushstate interpreter random util globals]
        clojush.instructions.tag
        [clojure.math numeric-tower]
        ))

; Atom generators
(def small-or-large-atom-generators
  (concat (list
            "small"
            "large"
            ;;; end constants
            (fn [] (- (lrand-int 20001) 10000)) ;Integer ERC [-10000,10000]
            ;;; end ERCs
            (tag-instruction-erc [:integer :boolean :exec] 1000)
            (tagged-instruction-erc 1000)
            ;;; end tag ERCs
            'in1
            ;;; end input instructions
            )


          ;; Kitchen sink ERCs
          (list
           (fn [] (- (lrand-int 257) 128)) ;Integer ERC [-128,128]
           (fn [] (- (lrand-int 20001) 10000)) ;Integer ERC [-10000,10000]
           (fn [] (- (* (lrand) 1000.0) 500.0)) ;Float ERC [-500.0,500.0)
           (fn [] (- (* (lrand) 20000.0) 10000.0)) ;Float ERC [-10000.0,10000.0)
           (fn [] (lrand-nth (list true false))) ;Boolean ERC
           (fn [] (lrand-nth (concat [\newline \tab] (map char (range 32 127))))) ;Visible character ERC
           (fn [] (apply str (repeatedly (lrand-int 50) (fn [] (lrand-nth (map char (range 32 127))))))) ;String ERC
           )
          ;; Kitchen sink instructions
          (registered-for-stacks [:float :integer :boolean :string :char :exec :print :code :parentheses :vector_boolean :vector_integer :vector_float :vector_string])

          ))


;; A list of data domains for the problem. Each domain is a vector containing
;; a "set" of inputs and two integers representing how many cases from the set
;; should be used as training and testing cases respectively. Each "set" of
;; inputs is either a list or a function that, when called, will create a
;; random element of the set.
(def small-or-large-data-domains
  [[(concat (list -10000 0 980) (range 995 1005) (list 1020 1980)
            (range 1995 2005) (list 2020 10000)) 27 0] ;; "Special" inputs covering most base cases.
   [(concat (range 980 1020) (range 1980 2020)) 0 80] ;; Some cases to test generality.
   [(fn [] (- (lrand-int 20001) 10000)) 73 920] ;; Inputs between -10,000 and 10,000
   ])

;;Can make Small Or Large test data like this:
;(test-and-train-data-from-domains small-or-large-data-domains)

; Helper function for error function
(defn small-or-large-test-cases
  "Takes a sequence of inputs and gives IO test cases of the form
   [input output]."
  [inputs]
  (map (fn [in]
         (vector in
                 (cond (< in 1000) "small"
                       (>= in 2000) "large"
                       :else "")))
       inputs))

(defn make-small-or-large-error-function-from-cases
  [train-cases test-cases]
  (fn the-actual-small-or-large-error-function
    ([individual]
      (the-actual-small-or-large-error-function individual :train))
    ([individual data-cases] ;; data-cases should be :train or :test
     (the-actual-small-or-large-error-function individual data-cases false))
    ([individual data-cases print-outputs]
      (let [behavior (atom '())
            errors (doall
                     (for [[input1 correct-output] (case data-cases
                                                     :train train-cases
                                                     :test test-cases
                                                     data-cases)]
                       (let [final-state (run-push (:program individual)
                                                   (->> (make-push-state)
                                                     (push-item input1 :input)
                                                     (push-item "" :output)))
                             result (stack-ref :output 0 final-state)]
                         (when print-outputs
                           (println (format "| Correct output: %s\n| Program output: %s\n" (pr-str correct-output) (pr-str result))))
                         ; Record the behavior
                         (swap! behavior conj result)
                         ; Error is Levenshtein distance of printed strings
                         (levenshtein-distance correct-output result))))]
        (if (= data-cases :test)
          (assoc individual :test-errors errors)
          (assoc individual :behaviors @behavior :errors errors)
          )))))

(defn get-small-or-large-train-and-test
  "Returns the train and test cases."
  [data-domains]
  (map sort (map small-or-large-test-cases
                 (test-and-train-data-from-domains data-domains))))

; Define train and test cases
(def small-or-large-train-and-test-cases
  (get-small-or-large-train-and-test small-or-large-data-domains))

(defn small-or-large-initial-report
  [argmap]
  (println "Train and test cases:")
  (doseq [[i case] (map vector (range) (first small-or-large-train-and-test-cases))]
    (println (format "Train Case: %3d | Input/Output: %s" i (str case))))
  (doseq [[i case] (map vector (range) (second small-or-large-train-and-test-cases))]
    (println (format "Test Case: %3d | Input/Output: %s" i (str case))))
  (println ";;******************************"))

(defn small-or-large-report
  "Custom generational report."
  [best population generation error-function report-simplifications]
  (let [best-test-errors (:test-errors (error-function best :test))
        best-total-test-error (apply +' best-test-errors)]
    (println ";;******************************")
    (printf ";; -*- Small Or Large problem report - generation %s\n" generation)(flush)
    (println "Test total error for best:" best-total-test-error)
    (println (format "Test mean error for best: %.5f" (double (/ best-total-test-error (count best-test-errors)))))
    (when (zero? (:total-error best))
      (doseq [[i error] (map vector
                             (range)
                             best-test-errors)]
        (println (format "Test Case  %3d | Error: %s" i (str error)))))
    (println ";;------------------------------")
    (println "Outputs of best individual on training cases:")
    (error-function best :train true)
    (println ";;******************************")
    )) ;; To do validation, could have this function return an altered best individual
       ;; with total-error > 0 if it had error of zero on train but not on validation
       ;; set. Would need a third category of data cases, or a defined split of training cases.


; Define the argmap
(def argmap
  {:error-function (make-small-or-large-error-function-from-cases (first small-or-large-train-and-test-cases)
                                                                  (second small-or-large-train-and-test-cases))
   :training-cases (first small-or-large-train-and-test-cases)
   :sub-training-cases '()
   :atom-generators small-or-large-atom-generators
   :max-points 800
   :max-genome-size-in-initial-program 100
   :evalpush-limit 300
   :population-size 1000
   :max-generations 300
   :parent-selection :lexicase
   :genetic-operator-probabilities {:alternation 0.2
                                    :uniform-mutation 0.2
                                    :uniform-close-mutation 0.1
                                    [:alternation :uniform-mutation] 0.5
                                    }
   :alternation-rate 0.01
   :alignment-deviation 5
   :uniform-mutation-rate 0.01
   :problem-specific-report small-or-large-report
   :problem-specific-initial-report small-or-large-initial-report
   :report-simplifications 0
   :final-report-simplifications 5000
   :max-error 5000
   })
