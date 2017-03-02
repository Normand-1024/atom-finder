(in-ns 'atom-finder.classifier)
(import '(org.eclipse.cdt.core.dom.ast IASTNode IASTBinaryExpression IASTUnaryExpression))

(defmulti radix "In which base is the number specified" class)
(s/defmethod radix String :- s/Keyword
  [num :- s/Str]
  ; This is a simplification. See for details:
  ; cdt/core/org.eclipse.cdt.core/parser/org/eclipse/cdt/internal/core/dom/parser/cpp/CPPASTLiteralExpression.java
  (condp re-find num
    #"^[+-]?0x" :hex
    #"^[+-]?0b" :bin
    #"^[+-]?0([^0-9]|$)" :dec ; Matches "0", "0.1" This may not technically be true buuuut...
    #"^[+-]?0.[0-9]*[.eE]" :dec ; There is no octal float in C
    #"^[+-]?0"  :oct
                :dec
  ))

(s/defmethod radix IASTNode :- s/Keyword [n]
  (radix (String. (.getValue n))))

(defmulti bitwise-op? "Check if an ASTNode represents a bitwise operator" class)
(s/defmethod bitwise-op? IASTBinaryExpression :- s/Bool [n] false)
(s/defmethod bitwise-op? IASTUnaryExpression :- s/Bool [n] false)

; &  bitwise AND
; |  bitwise inclusive OR
; ^  bitwise XOR (eXclusive OR)
; <<  left shift
; >>  right shift
; ~  bitwise NOT (one's complement) (unary)

