(in-ns 'atom-finder.classifier)
(import '(org.eclipse.cdt.core.dom.ast IASTBinaryExpression IASTConditionalExpression IASTUnaryExpression)
        '(org.eclipse.cdt.internal.core.dom.parser.cpp CPPASTConditionalExpression CPPASTExpressionList CPPASTFieldReference))

(defmulti operator-group"Returns the name of the operator group that the node belongs to, nil not defined in this operator-group" class)
(defmethod operator-group :default [node]
  (let [operator-group-table
    {CPPASTConditionalExpression :cond
     CPPASTExpressionList :comma
     CPPASTFieldReference :field_ref}]
    (->> node type operator-group-table)))
(defmethod operator-group IASTUnaryExpression [node]
  (let [operator-group-table
    {IASTUnaryExpression/op_postFixDecr :de_incr
     IASTUnaryExpression/op_postFixIncr :de_incr
     IASTUnaryExpression/op_minus :arith_unary
     IASTUnaryExpression/op_plus :arith_unary
     IASTUnaryExpression/op_prefixDecr :de_incr
     IASTUnaryExpression/op_prefixIncr :de_incr
     IASTUnaryExpression/op_amper :pointer
     IASTUnaryExpression/op_star :pointer
     IASTUnaryExpression/op_not :not
     IASTUnaryExpression/op_tilde :bitwise_not}]
    (->> node .getOperator operator-group-table)))
(defmethod operator-group IASTBinaryExpression [node]
  (if (assignment? node)
    :assign

    (let [operator-group-table
               {IASTBinaryExpression/op_modulo :non-asso
                IASTBinaryExpression/op_multiply :multiply
                IASTBinaryExpression/op_divide :non-asso
                IASTBinaryExpression/op_plus :add
                IASTBinaryExpression/op_minus :non-asso
                IASTBinaryExpression/op_shiftLeft :bitwise_bin
                IASTBinaryExpression/op_shiftRight :bitwise_bin
                IASTBinaryExpression/op_greaterThan :compare
                IASTBinaryExpression/op_greaterEqual :compare 
                IASTBinaryExpression/op_lessThan :compare
                IASTBinaryExpression/op_lessEqual :compare
                IASTBinaryExpression/op_equals :compare
                IASTBinaryExpression/op_notequals :compare
                IASTBinaryExpression/op_binaryAnd :bitwise_bin
                IASTBinaryExpression/op_binaryXor :bitwise_bin
                IASTBinaryExpression/op_binaryOr :bitwise_bin
                IASTBinaryExpression/op_logicalAnd :and
                IASTBinaryExpression/op_logicalOr :or}]
           (->> node .getOperator operator-group-table))))

(def order-insensitive-opt-combination
  (into #{} (map sort [[:multiply :add] [:and :add] [:and :multiply] [:or :add]
                       [:or :multiply] [:or :and] [:not :arith_unary]
                       ;;[:compare :and] [:compare :or] [:compare :compare]// Underclassify
                       [:cond :cond] [:non-asso :multiply]
                       [:non-asso :and] [:non-asso :or] [:non-asso :non-asso]
                       [:field_ref :pointer]

                       [:bitwise_not :arith_unary] [:bitwise_not :not] [:bitwise_not :pointer]
                       [:bitwise_bin :add] [:bitwise_bin :multiply] [:bitwise_bin :and] [:biwise_bin :or]
                       [:bitwise_bin :compare] [:bitwise_bin :pointer] [:bitwise_bin :non-asso]])))

(def order-sensitive-opt-combination
  #{[:pointer :de_incr] [:arith_unary :add] [:arith_unary :multiply] [:arith_unary :and]
    [:arith_unary :or] [:not :add] [:not :multiply] [:not :and] [:not :or]
    [:not :compare] [:pointer :add] [:arith_unary :cond] [:and :cond]
    [:or :cond] [:not :cond] [:compare :cond] [:non-asso :add]
    [:arith_unary :non-asso] [:not :non-asso]

    [:bitwise_not :add] [:bitwise_not :multiply] [:bitwise_not :or] [:bitwise_not :cond]
    [:bitwise_not :compare] [:bitwise_not :non-asso] [:bitwise-not :field_ref]
    [:bitwise_not :de_incr] [:arith_unary :bitwise_bin] [:not :bitwise_bin] [:bitwise_bin :cond]
    [:bitwise_not :bitwise_bin]})

(defn confusing-operator-combination?
  "Is this combination of the operator groups confusing?"
  [combination]
  (or (order-sensitive-opt-combination combination)
      (order-insensitive-opt-combination (sort combination))

      ;;SPECIAL CASE 1: Comma operator in any combination;
      (some (partial = :comma) combination)))

(defn operator-group-pair?
  [node]
  (and (operator-group node)
       (some operator-group (children node))))

(def rvalue-unary-ops?
  "HELPER:Is this a unary operator that can operate on an rvalue? (! - + *)"
   (comp #{:arith_unary :not :pointer :bitwise_not} operator-group))

(defn unparenthesized-unary-in-children
  "HELPER:Is this node not a bracket and constains unary operator in its children?"
  [node]
  (if (not (paren-node? node))
      (filter #(rvalue-unary-ops? %) (children node))))

(defn unary-before-binary-pairs
  "Special function for returning a collection of unary operator in group pair in the first operand of a binary operator"
  [node]
  (->> (unparenthesized-unary-in-children (get-in-tree [0] node))
       (map operator-group)
       (remove nil?)
       (map (fn [child-group] [child-group (operator-group node)]))))

(defn group-pairs-in-node
  "Returns all operator group pairs in a node"
  [node child-groups]
  (let [node-group (operator-group node)]
    (cond
           (instance? IASTUnaryExpression node) 
           [[node-group (safe-nth child-groups 0)]]

           (instance? IASTBinaryExpression node) 
           (-> node
                unary-before-binary-pairs
                (conj [(safe-nth child-groups 0) node-group] [node-group (safe-nth child-groups 1)]))

           (instance? CPPASTConditionalExpression node) 
           [[(safe-nth child-groups 0) node-group] [node-group (safe-nth child-groups 1)] [node-group (safe-nth child-groups 2)]]

           :else (map (fn [child-group] [node-group child-group]) child-groups))))

(defn group-pair
  "returns a collection of operator group pairs between the node and its children
  , if a second parameter is passed, use that as the collection instead of the children"
  ([node]
   (group-pair node (children node)))

  ([node collection]
   (->> collection
        (map operator-group)
        (group-pairs-in-node node)
        (remove (partial some nil?)))))

(defn operator-precedence-atom?
  "Is this node an operator-precedence-atom?"
  [node]
  (and
   (operator-group-pair? node)

   (not (= :assign (operator-group node)))

   (some confusing-operator-combination? (group-pair node))))