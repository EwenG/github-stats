(ns ewen.github-stats.env
  (:require [clojure.edn :as edn]))


(def env-file "env.clj")

(let [get-env-fn (fn [env-file] (-> (clojure.java.io/resource env-file)
                            slurp
                            edn/read-string))
      cache-env (get-env-fn env-file)]
  (defn env
    ([]
     (env false))
    ([cache] (if cache cache-env
                       (get-env-fn env-file)))))
