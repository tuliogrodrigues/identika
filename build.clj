(ns build
  "Build script for Identika — jar and install tasks.

  Usage:
    clojure -T:build clean          # Remove target/
    clojure -T:build jar            # Build target/identika-<version>.jar
    clojure -T:build install        # Install to local ~/.m2/repository

  Deploy to Clojars / Maven Central requires Maven installed:
    mvn deploy:deploy-file \\
      -Dfile=target/identika-<version>.jar \\
      -DpomFile=target/classes/META-INF/maven/com.tgr/identika/pom.xml \\
      -DrepositoryId=clojars \\
      -Durl=https://clojars.org/repo

  Version is read from PROJECT_VERSION env var, or defaults to \"0.2.0\"."
  (:require [clojure.java.io :as io]
            [clojure.tools.build.api :as b]))

(def lib 'com.tgr/identika)
(def version (or (System/getenv "PROJECT_VERSION") "0.2.0"))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))

(defn- pom-template [v]
  [[:description "A lightweight, zero-dependency Clojure toolkit for generating and parsing unique identifiers (ULID, UUID v4, and more)."]
   [:url "https://github.com/tuliogrodrigues/identika"]
   [:licenses
    [:license
     [:name "MIT License"]
     [:url "https://opensource.org/licenses/MIT"]]]
   [:developers
    [:developer
     [:name "Tulio Rodrigues"]
     [:email "tulio.g.rodrigues@gmail.com"]]]
   [:scm
    [:url "https://github.com/tuliogrodrigues/identika"]
    [:connection "scm:git:git@github.com:tuliogrodrigues/identika.git"]
    [:developerConnection "scm:git:ssh:git@github.com:tuliogrodrigues/identika.git"]
    [:tag (str "v" v)]]])

(defn clean
  "Remove target/ directory."
  [_]
  (b/delete {:path "target"}))

(defn jar
  "Build the library JAR: clean, write pom.xml, copy sources, create jar."
  [{:keys [project-version] :or {project-version version}}]
  (let [v (or project-version version)
        jar-file (format "target/%s-%s.jar" (name lib) v)]
    (clean nil)
    (println "\n── Writing pom.xml ──")
    (b/write-pom {:basis basis
                  :class-dir class-dir
                  :lib lib
                  :version v
                  :src-dirs ["src"]
                  :pom-data (pom-template v)})
    (println "── Copying sources ──")
    (b/copy-dir {:src-dirs ["src" "resources"]
                 :target-dir class-dir})
    ;; Remove tooling caches from the build (clj-kondo, lsp, etc.)
    (doseq [subdir [".clj-kondo" ".lsp" ".cpcache"]]
      (b/delete {:path (str (io/file class-dir "identika" subdir))}))
    (println (format "── Building %s ──" jar-file))
    (b/jar {:class-dir class-dir
            :jar-file jar-file})
    (println "Done.")))

(defn install
  "Install the JAR to the local Maven repository (~/.m2/repository)."
  [{:keys [project-version] :or {project-version version}}]
  (let [v (or project-version version)]
    (println (format "Installing %s/%s %s to local Maven repo..." lib v))
    (b/install {:basis basis
                :lib lib
                :version v
                :class-dir class-dir
                :jar-file (format "target/%s-%s.jar" (name lib) v)
                :pom-data (pom-template v)})
    (println "Done.")))
