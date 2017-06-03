(in-ns 'atom-finder.util)

(import '(org.eclipse.cdt.internal.core.dom.rewrite
          ASTModificationStore astwriter.ASTWriter astwriter.ASTWriterVisitor
          commenthandler.NodeCommentMap changegenerator.ChangeGeneratorWriterVisitor))

(defn print-tree [node]
  (letfn
      [(f [node index]
         (let [offset (format " (offset: %s, %s)"
                              (-> node .getFileLocation .getNodeOffset)
                              (-> node .getFileLocation .getNodeLength))]

           (printf "%s -%s %s -> %s\n"
                   (apply str (repeat index "  "))
                   (-> node .getClass .getSimpleName)
                   offset
                   (-> node .getRawSignature
                       (str "           ")
                       (.subSequence 0 10)
                       (.replaceAll "\n" " \\ ")))))]

    (pre-tree f node)))

(def ast-writer (ASTWriter.))
(defn write-ast [node] (.write ast-writer node))

(defmacro should-visit! [writer val]
  "Tell the AST-visitor to visit no types of node"
  (let [fields
        ['shouldVisitArrayModifiers 'shouldVisitBaseSpecifiers
         'shouldVisitDeclarations 'shouldVisitDeclarators 'shouldVisitDeclSpecifiers
         'shouldVisitDecltypeSpecifiers 'shouldVisitExpressions
         'shouldVisitInitializers 'shouldVisitNames 'shouldVisitNamespaces
         'shouldVisitParameterDeclarations 'shouldVisitPointerOperators
         'shouldVisitStatements 'shouldVisitTemplateParameters
         'shouldVisitTranslationUnit 'shouldVisitTypeIds 'shouldVisitAttributes]]
    (concat '(do)
            (for [field fields]
              `(set! (. ~writer ~field) ~val))
            [writer])))

(defn visit-nothing! [writer] (should-visit! writer false))
(defn visit-everything! [writer] (should-visit! writer true))

(defmulti  printable-node? "Is this node non-degenerate" class)
(defmethod printable-node? :default [node] true) ;; most nodes can be printed
(defmethod printable-node? IASTBinaryExpression
  [node] (.getOperand2 node)) ;; if there's no second operand it can't be printed. e.g. "x = {}"

(defn SingleNodeVisitor []
  (let [modificationStore (ASTModificationStore.)
        commentMap (NodeCommentMap.)
        ;writer-visitor (ChangeGeneratorWriterVisitor. modificationStore nil commentMap)]
        ]
    (proxy [ChangeGeneratorWriterVisitor] [modificationStore nil commentMap]
      (visit [node]
        (if (printable-node? node)
          (do
            (visit-nothing! this)
            (proxy-super visit node))
          0))
      )))

(s/defn write-node-type :- s/Str [node :- (s/maybe IASTNode)] (str "<" (and node (typename node)) ">"))

(s/defn write-node :- s/Str [node :- IASTNode]
  (if (or (nil? node) (instance? IASTProblemHolder node))
    (write-node-type node)
    (let [writer-visitor (SingleNodeVisitor)]
      (.accept node writer-visitor)

      (let [node-str (str/replace (or (.toString writer-visitor) "") #"\s" "")]
        (if (empty? node-str)
          (write-node-type node)
          node-str))
      )))

(s/defn write-node-valueless :- s/Str
  "Don't write variable names, instead use their type"
  [node :- IASTNode]
  ((if (exists? #(instance? % node) [IASTName IASTLiteralExpression])
    write-node-type
    write-node)
   node))

(s/defn write-nodes-with-depth :- [s/Str]
  ([root :- IASTNode] (write-nodes-with-depth 0 root))
  ([depth root]
   (concat [(str (str/join (repeat depth " ")) (write-node root))]
           (mapcat (partial write-nodes-with-depth (inc depth)) (children root)))))

