(ns antq.dep.github-action
  (:require
   [antq.dep.github-action.matrix :as d.gha.matrix]
   [antq.dep.github-action.third-party :as d.gha.third-party]
   [antq.dep.github-action.uses :as d.gha.uses]
   [antq.util.dep :as u.dep]
   [clj-yaml.core :as yaml]
   [clojure.java.io :as io]
   [clojure.walk :as walk])
  (:import
   java.io.File))

(def ^:private detect-functions
  [d.gha.uses/detect
   d.gha.third-party/detect])

(defn- detect-deps
  [form]
  (reduce
   (fn [accm f]
     (concat accm (f form)))
   []
   detect-functions))

(defn extract-deps
  [file-path workflow-content-str]
  (let [deps (atom [])
        parsed (yaml/parse-string workflow-content-str)]
    (doseq [[job-name job-body] (:jobs parsed)]
      (walk/prewalk (fn [form]
                      (when-let [deps* (seq (detect-deps form))]
                        (->> deps*
                             (d.gha.matrix/expand-matrix-value parsed job-name)
                             (swap! deps concat)))
                      form)
                    job-body))
    (map #(assoc %
                 :project :github-action
                 :file file-path)
         @deps)))

(defn load-deps
  ([] (load-deps "."))
  ([dir]
   (let [dir-file (io/file dir ".github" "workflows")]
     (when (.isDirectory dir-file)
       (->> (file-seq dir-file)
            (filter #(.isFile ^File %))
            (mapcat #(extract-deps (u.dep/relative-path %)
                                   (slurp %))))))))
