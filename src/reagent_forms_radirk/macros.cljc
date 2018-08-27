(ns reagent-forms-radirk.macros
  (:require [clojure.walk :refer [postwalk]]))

(defmacro render-element [attrs doc & body]
  `(fn []
     (let [update-disabled?# (not (some #{(:field ~attrs)}
                                        [:multi-select :single-select]))
           body#             (postwalk
                               (fn [c#]
                                 (if (map? c#)
                                   (-> c#
                                       (reagent-forms-radirk.core/set-validation-class ~doc)
                                       (reagent-forms-radirk.core/update-attrs ~doc)
                                       (reagent-forms-radirk.core/set-disabled update-disabled?#)
                                       (reagent-forms-radirk.core/clean-attrs))
                                   c#))
                               ~@body)]
       (if-let [visible# (:visible? ~attrs)]
         (when (reagent-forms-radirk.core/call-attr ~doc visible#)
           body#)
         body#))))
