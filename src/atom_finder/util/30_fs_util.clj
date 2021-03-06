(in-ns 'atom-finder.util)

(defmacro log-err [msg ret x]
  `(try ~x
      (catch Exception e# (do (errln (str "-- exception parsing commit: \"" ~msg "\"\n")) ~ret))
      (catch Error e#     (do (errln (str "-- error parsing commit: \""  ~msg "\"\n")) ~ret)))
  #_x
  )

(defn c-file?
  [filename]
  (let [exts #{"c" "cc" "cpp" "C" "c++" "h" "hh" "hpp" "h++" "H"}]
    (exts (nth (re-find #".*\.([^.]+)" filename) 1 nil))))

(defn list-dirs
  [path]
  (->>
   path
   clojure.java.io/file
   .listFiles
   (filter #(.isDirectory %))))

(defn files-in-dir
  [dirname]
  (->> dirname clojure.java.io/file file-seq))

(defn c-files
  "Search directory structure for C-like files"
  [dirname]
  (filter (fn [file] (and (c-file? (.getName file)) (.isFile file)))
          (files-in-dir dirname)))

(defn resource-path
  "Find the path to a resource"
  [filename]
  (some->> filename clojure.java.io/resource clojure.java.io/file .getPath))

(def slurp-resource (comp slurp resource-path))

(defn slurp-lines [file]
  (line-seq (clojure.java.io/reader file)))

(def home-dir (System/getProperty "user.home"))

(defn expand-home [s]
  (if (clojure.string/starts-with? s "~")
    (str/replace-first s "~" home-dir)
    s))

(defn remove-home [s]
  (if (clojure.string/starts-with? s home-dir)
    (str/replace-first s home-dir "")
    s))

(s/defn pmap-dir-files
  "Apply a function to the filename of every c file in a directory"
  [f dirname]
  (pmap
   (fn [file]
     (let [filename (.getPath file)]
       (log-err (format "pmap-dir-file: \"%s\"" filename) nil
                (f filename))))
   (c-files dirname)))

(defn file-ext [file-str]
  "Get the file extension from a filename"
  (some->>
   file-str
   (re-find #"(.*/)?[^/]+\.([^.]+)")
   last))

(defmacro log-to [filename & stuff]
  `(binding [*out* (clojure.java.io/writer ~filename)]
     ~(cons 'do stuff)))
