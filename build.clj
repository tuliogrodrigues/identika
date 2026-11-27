(ns build
  "Build script for Identika — jar, install, and deploy tasks.

  Usage:
    clojure -T:build clean          # Remove target/
    clojure -T:build jar            # Build target/identika-<version>.jar
    clojure -T:build install        # Install to local ~/.m2/repository
    clojure -T:build deploy         # Build and deploy to Clojars

  Deploying to Clojars requires a deploy token (https://clojars.org/tokens)
  exposed as environment variables:

    CLOJARS_USERNAME=<clojars-username> \\
    CLOJARS_PASSWORD=<deploy-token> \\
    clojure -T:build deploy

  Version is read from the PROJECT_VERSION env var or an explicit
  :project-version option, e.g.:

    PROJECT_VERSION=$(git describe --tags --abbrev=0 --match 'v*' | sed 's/^v//') \\
    clojure -T:build jar"
  (:require [clojure.java.io :as io]
            [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'org.clojars.rodriguesgot/identika)
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))

(defn- resolve-version
  "Resolve the project version from an explicit :project-version option or the
  PROJECT_VERSION environment variable."
  [project-version]
  (or project-version
      (System/getenv "PROJECT_VERSION")
      (throw (ex-info (str "Project version is not set. Set the PROJECT_VERSION "
                           "environment variable or pass :project-version.")
                      {:env "PROJECT_VERSION"}))))

(defn- pom-template [v]
  [[:description "A lightweight, zero-dependency Clojure toolkit for generating and parsing unique identifiers (ULID, UUID v4, UUIDv7, KSUID, and more)."]
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
  [{:keys [project-version]}]
  (let [v (resolve-version project-version)
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
    ;; Bundle license and readme at the jar root
    (doseq [f ["LICENSE" "README.md"]]
      (io/copy (io/file f) (io/file class-dir f)))
    (println (format "── Building %s ──" jar-file))
    (b/jar {:class-dir class-dir
            :jar-file jar-file})
    (println "Done.")))

(defn install
  "Install the JAR to the local Maven repository (~/.m2/repository)."
  [{:keys [project-version]}]
  (let [v (resolve-version project-version)]
    (println (format "Installing %s %s to local Maven repo..." lib v))
    (b/install {:basis basis
                :lib lib
                :version v
                :class-dir class-dir
                :jar-file (format "target/%s-%s.jar" (name lib) v)
                :pom-data (pom-template v)})
    (println "Done.")))

(defn deploy
  "Build the JAR and deploy it to Clojars.

  Requires CLOJARS_USERNAME and CLOJARS_PASSWORD (a Clojars deploy token,
  see https://clojars.org/tokens) in the environment."
  [opts]
  (doseq [env ["CLOJARS_USERNAME" "CLOJARS_PASSWORD"]]
    (when-not (System/getenv env)
      (throw (ex-info (str env " is not set. Create a deploy token at "
                           "https://clojars.org/tokens, then export "
                           "CLOJARS_USERNAME and CLOJARS_PASSWORD.")
                      {:env env}))))
  (jar opts)
  (let [v (resolve-version (:project-version opts))]
    (println (format "\n── Deploying %s %s to Clojars ──" lib v))
    (dd/deploy {:installer :remote
                :artifact (format "target/%s-%s.jar" (name lib) v)
                :pom-file (b/pom-path {:lib lib :class-dir class-dir})
                ;; Clojars does not require GPG signatures
                :sign-releases? false})
    (println "Done.")))
