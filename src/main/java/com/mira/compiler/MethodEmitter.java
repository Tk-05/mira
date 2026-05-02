package com.mira.compiler;

import java.util.List;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.IF_ICMPGE;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.IXOR;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.SIPUSH;
import static org.objectweb.asm.Opcodes.SWAP;

import com.mira.lexer.token.TokenType;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.AwaitExpression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.FieldAccessExpression;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.parser.nodes.expression.Expression.MethodCallExpression;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.RangeExpression;
import com.mira.parser.nodes.expression.Expression.TernaryExpression;
import com.mira.parser.nodes.expression.Expression.ThrownException;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.Continue;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.Foreach;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Overwrite;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.runtime.visitors.ExprVisitor;
import com.mira.runtime.visitors.StmtVisitor;

public class MethodEmitter implements ExprVisitor<Void>, StmtVisitor<Void> {

    private final CompilerContext ctx;
    private final ClassEmitter ce;
    private final MethodVisitor mv;

    private static final String RT = ClassEmitter.RT_NAME;
    private static final String ENV = ClassEmitter.ENV_NAME;
    private static final String ENV_D = ClassEmitter.ENV_DESC;
    private static final String OBJ = "java/lang/Object";
    private static final String OBJ_D = "Ljava/lang/Object;";

    public MethodEmitter(CompilerContext ctx, ClassEmitter ce) {
        this.ctx = ctx;
        this.ce = ce;
        this.mv = ctx.mv;
    }

    private void emitGlobals() {
        if (ctx.objectEnvSlot >= 0) {
            mv.visitVarInsn(ALOAD, ctx.objectEnvSlot);
        } else {
            mv.visitFieldInsn(GETSTATIC, ctx.className, "GLOBALS", ENV_D);
        }
    }

    private void emitRealGlobals() {
        mv.visitFieldInsn(GETSTATIC, ctx.className, "GLOBALS", ENV_D);
    }

    private void emitNullVal() {
        mv.visitMethodInsn(INVOKESTATIC, RT, "nullVal", "()" + OBJ_D, false);
    }

    private void emitIsTruthy() {
        mv.visitMethodInsn(INVOKESTATIC, RT, "isTruthy", "(" + OBJ_D + ")Z", false);
    }

    private void emitIntConst(int n) {
        if (n >= 0 && n <= 5) {
            mv.visitInsn(ICONST_0 + n);
        } else if (n >= Byte.MIN_VALUE && n <= Byte.MAX_VALUE) {
            mv.visitIntInsn(BIPUSH, n);
        } else if (n >= Short.MIN_VALUE && n <= Short.MAX_VALUE) {
            mv.visitIntInsn(SIPUSH, n);
        } else {
            mv.visitLdcInsn(n);
        }
    }

    private void emitObjectArray(List<Expression> args) {
        emitIntConst(args.size());
        mv.visitTypeInsn(ANEWARRAY, OBJ);
        for (int i = 0; i < args.size(); i++) {
            mv.visitInsn(DUP);
            emitIntConst(i);
            args.get(i).accept(this);
            mv.visitInsn(AASTORE);
        }
    }

    private void emitVarLookup(String name) {
        Integer slot = ctx.slots.slotOf(name);
        if (slot != null) {
            mv.visitVarInsn(ALOAD, slot);
        } else {
            emitGlobals();
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(INVOKEVIRTUAL, ENV, "get",
                    "(Ljava/lang/String;)" + OBJ_D, false);
        }
    }

    private void emitVarStore(String name, boolean isNewDecl) {
        if (!ctx.isTopLevel || ctx.blockDepth > 0) {
            if (isNewDecl) {
                int slot = ctx.slots.allocate(name);
                mv.visitVarInsn(ASTORE, slot);
            } else {
                Integer slot = ctx.slots.slotOf(name);
                if (slot != null) {
                    mv.visitVarInsn(ASTORE, slot);
                } else {
                    int tmp = ctx.slots.allocate("$$tmp_" + name);
                    mv.visitVarInsn(ASTORE, tmp);
                    emitGlobals();
                    mv.visitLdcInsn(name);
                    mv.visitVarInsn(ALOAD, tmp);
                    mv.visitMethodInsn(INVOKEVIRTUAL, ENV, "assign",
                            "(Ljava/lang/String;" + OBJ_D + ")V", false);
                }
            }
        } else {
            int tmp = ctx.slots.allocate("$$tmp_" + name);
            mv.visitVarInsn(ASTORE, tmp);
            emitGlobals();
            mv.visitLdcInsn(name);
            mv.visitVarInsn(ALOAD, tmp);
            String method = isNewDecl ? "define" : "assign";
            mv.visitMethodInsn(INVOKEVIRTUAL, ENV, method,
                    "(Ljava/lang/String;" + OBJ_D + ")V", false);
        }
    }

    public void emitBody(List<Node> body) {
        for (Node node : body) {
            emitNode(node);
        }
    }

    public void emitNode(Node node) {
        switch (node) {
            case Expression expr -> {
                expr.accept(this);
                mv.visitInsn(POP);
            }
            case Statement stmt ->
                stmt.accept(this);
            default -> {
            }
        }
    }

    @Override
    public <T> T visitDumbExpr(DumbExpression expression) {
        String val = expression.getValue();
        switch (val) {
            case "true" -> {
                mv.visitInsn(ICONST_1);
                mv.visitMethodInsn(INVOKESTATIC, RT, "wrapBool", "(Z)" + OBJ_D, false);
            }
            case "false" -> {
                mv.visitInsn(ICONST_0);
                mv.visitMethodInsn(INVOKESTATIC, RT, "wrapBool", "(Z)" + OBJ_D, false);
            }
            case "null" ->
                emitNullVal();
            default -> {
                if (expression.getTokenType() == TokenType.EXPRESSION
                        && !val.isEmpty() && Character.isDigit(val.charAt(0))) {
                    emitNumberLiteral(val);
                } else {
                    mv.visitLdcInsn(val);
                }
            }
        }
        return null;
    }

    private void emitNumberLiteral(String val) {
        if (val.startsWith("0x") || val.startsWith("0X")) {
            mv.visitLdcInsn(Long.parseLong(val.substring(2), 16));
            mv.visitMethodInsn(INVOKESTATIC, RT, "wrapLong", "(J)" + OBJ_D, false);
        } else if (val.contains(".")) {
            mv.visitLdcInsn(Double.parseDouble(val));
            mv.visitMethodInsn(INVOKESTATIC, RT, "wrapDouble", "(D)" + OBJ_D, false);
        } else {
            mv.visitLdcInsn(Long.parseLong(val));
            mv.visitMethodInsn(INVOKESTATIC, RT, "wrapLong", "(J)" + OBJ_D, false);
        }
    }

    @Override
    public <T> T visitBinaryExpr(BinaryExpression expression) {
        String op = expression.getOperator().getLexeme();

        switch (op) {
            case "&&" -> {
                Label shortFalse = new Label(), end = new Label();
                expression.getLeft().accept(this);
                emitIsTruthy();
                mv.visitJumpInsn(IFEQ, shortFalse);
                expression.getRight().accept(this);
                emitIsTruthy();
                mv.visitJumpInsn(IFEQ, shortFalse);
                mv.visitInsn(ICONST_1);
                mv.visitMethodInsn(INVOKESTATIC, RT, "wrapBool", "(Z)" + OBJ_D, false);
                mv.visitJumpInsn(GOTO, end);
                mv.visitLabel(shortFalse);
                mv.visitInsn(ICONST_0);
                mv.visitMethodInsn(INVOKESTATIC, RT, "wrapBool", "(Z)" + OBJ_D, false);
                mv.visitLabel(end);
            }
            case "||" -> {
                Label shortTrue = new Label(), end = new Label();
                expression.getLeft().accept(this);
                emitIsTruthy();
                mv.visitJumpInsn(IFNE, shortTrue);
                expression.getRight().accept(this);
                emitIsTruthy();
                mv.visitJumpInsn(IFNE, shortTrue);
                mv.visitInsn(ICONST_0);
                mv.visitMethodInsn(INVOKESTATIC, RT, "wrapBool", "(Z)" + OBJ_D, false);
                mv.visitJumpInsn(GOTO, end);
                mv.visitLabel(shortTrue);
                mv.visitInsn(ICONST_1);
                mv.visitMethodInsn(INVOKESTATIC, RT, "wrapBool", "(Z)" + OBJ_D, false);
                mv.visitLabel(end);
            }
            case "??" -> {
                Label notNull = new Label(), end = new Label();
                expression.getLeft().accept(this);
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESTATIC, RT, "isNullValue", "(" + OBJ_D + ")Z", false);
                mv.visitJumpInsn(IFEQ, notNull);
                mv.visitInsn(POP);
                expression.getRight().accept(this);
                mv.visitJumpInsn(GOTO, end);
                mv.visitLabel(notNull);
                mv.visitLabel(end);
            }
            case "|>" -> {
                expression.getLeft().accept(this);
                if (expression.getRight() instanceof CallExpression call) {
                    emitCalleeObject(call.getCallee());
                    emitObjectArray(call.getArguments());
                } else {
                    expression.getRight().accept(this);
                    mv.visitInsn(ICONST_0);
                    mv.visitTypeInsn(ANEWARRAY, OBJ);
                }
                mv.visitMethodInsn(INVOKESTATIC, RT, "pipe",
                        "(" + OBJ_D + OBJ_D + "[" + OBJ_D + ")" + OBJ_D, false);
            }
            default -> {
                expression.getLeft().accept(this);
                expression.getRight().accept(this);
                String rtMethod = switch (op) {
                    case "+" ->
                        "add";
                    case "-" ->
                        "sub";
                    case "*" ->
                        "mul";
                    case "/" ->
                        "div";
                    case "%" ->
                        "mod";
                    case "**" ->
                        "pow";
                    case "\\%" ->
                        "floorDiv";
                    case "==" ->
                        "eq";
                    case "!=" ->
                        "neq";
                    case "<" ->
                        "lt";
                    case ">" ->
                        "gt";
                    case "<=" ->
                        "lte";
                    case ">=" ->
                        "gte";
                    case "&" ->
                        "bitwiseAnd";
                    case "|" ->
                        "bitwiseOr";
                    case "^" ->
                        "bitwiseXor";
                    case "<<" ->
                        "shiftLeft";
                    case ">>" ->
                        "shiftRight";
                    default ->
                        throw new RuntimeException("Unknown binary operator: " + op);
                };
                mv.visitMethodInsn(INVOKESTATIC, RT, rtMethod,
                        "(" + OBJ_D + OBJ_D + ")" + OBJ_D, false);
            }
        }
        return null;
    }

    @Override
    public <T> T visitUnaryExpr(UnaryExpression expression) {
        String op = expression.getOperation().getLexeme();
        switch (op) {
            case "$" -> {
                if (expression.getRight() instanceof DumbExpression dumb) {
                    emitVarLookup(dumb.getValue());
                } else {
                    expression.getRight().accept(this);
                    mv.visitTypeInsn(org.objectweb.asm.Opcodes.CHECKCAST, "java/lang/String");
                    emitGlobals();
                    mv.visitInsn(SWAP);
                    mv.visitMethodInsn(INVOKEVIRTUAL, ENV, "get",
                            "(Ljava/lang/String;)" + OBJ_D, false);
                }
            }
            case "-" -> {
                if (expression.getRight() == null) {
                    mv.visitLdcInsn("-");
                } else {
                    expression.getRight().accept(this);
                    mv.visitMethodInsn(INVOKESTATIC, RT, "negate", "(" + OBJ_D + ")" + OBJ_D, false);
                }
            }
            case "!" -> {
                expression.getRight().accept(this);
                emitIsTruthy();
                mv.visitInsn(ICONST_1);
                mv.visitInsn(IXOR);
                mv.visitMethodInsn(INVOKESTATIC, RT, "wrapBool", "(Z)" + OBJ_D, false);
            }
            case "~" -> {
                expression.getRight().accept(this);
                mv.visitMethodInsn(INVOKESTATIC, RT, "bitwiseNot", "(" + OBJ_D + ")" + OBJ_D, false);
            }
            case "++" ->
                emitIncDec(expression, true);
            case "--" ->
                emitIncDec(expression, false);
            default ->
                throw new RuntimeException("Unknown unary operator: " + op);
        }
        return null;
    }

    private void emitIncDec(UnaryExpression expression, boolean inc) {
        if (!(expression.getRight() instanceof UnaryExpression varExpr)
                || !varExpr.getOperation().getLexeme().equals("$")) {
            throw new RuntimeException("++ / -- requires a variable");
        }
        String name = ((DumbExpression) varExpr.getRight()).getValue();
        emitVarLookup(name);
        mv.visitLdcInsn(1L);
        mv.visitMethodInsn(INVOKESTATIC, RT, "wrapLong", "(J)" + OBJ_D, false);
        mv.visitMethodInsn(INVOKESTATIC, RT, inc ? "add" : "sub",
                "(" + OBJ_D + OBJ_D + ")" + OBJ_D, false);
        mv.visitInsn(DUP);
        emitVarStore(name, false);
    }

    @Override
    public <T> T visitTernaryExpr(TernaryExpression expression) {
        Label elseLabel = new Label(), end = new Label();
        expression.getCondition().accept(this);
        emitIsTruthy();
        mv.visitJumpInsn(IFEQ, elseLabel);
        expression.getThenExpr().accept(this);
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(elseLabel);
        expression.getElseExpr().accept(this);
        mv.visitLabel(end);
        return null;
    }

    @Override
    public <T> T visitCallExpr(CallExpression expression) {
        Expression callee = expression.getCallee();
        if (callee instanceof DumbExpression dumb && !isNumberOrKeyword(dumb.getValue())) {
            String name = dumb.getValue();
            if (ctx.knownFunctions.contains(name)) {
                emitObjectArray(expression.getArguments());
                mv.visitMethodInsn(INVOKESTATIC, ctx.className,
                        "mira$" + name, ClassEmitter.FN_DESC, false);
            } else {
                Integer slot = ctx.slots.slotOf(name);
                if (slot != null) {
                    mv.visitVarInsn(ALOAD, slot);
                    emitObjectArray(expression.getArguments());
                    mv.visitMethodInsn(INVOKESTATIC, RT, "dynamicCall",
                            "(" + OBJ_D + "[" + OBJ_D + ")" + OBJ_D, false);
                } else {
                    emitRealGlobals();
                    mv.visitLdcInsn(name);
                    emitObjectArray(expression.getArguments());
                    mv.visitMethodInsn(INVOKESTATIC, RT, "callNamed",
                            "(" + ENV_D + "Ljava/lang/String;[" + OBJ_D + ")" + OBJ_D, false);
                }
            }
        } else {
            emitCalleeObject(callee);
            emitObjectArray(expression.getArguments());
            mv.visitMethodInsn(INVOKESTATIC, RT, "dynamicCall",
                    "(" + OBJ_D + "[" + OBJ_D + ")" + OBJ_D, false);
        }
        return null;
    }

    private void emitCalleeObject(Expression callee) {
        if (callee instanceof DumbExpression dumb && !isNumberOrKeyword(dumb.getValue())) {
            String name = dumb.getValue();
            Integer slot = ctx.slots.slotOf(name);
            if (slot != null) {
                mv.visitVarInsn(ALOAD, slot);
            } else {
                emitGlobals();
                mv.visitLdcInsn(name);
                mv.visitMethodInsn(INVOKEVIRTUAL, ENV, "get",
                        "(Ljava/lang/String;)" + OBJ_D, false);
            }
        } else {
            callee.accept(this);
        }
    }

    private boolean isNumberOrKeyword(String val) {
        return val.equals("true") || val.equals("false") || val.equals("null")
                || (!val.isEmpty() && Character.isDigit(val.charAt(0)));
    }

    @Override
    public <T> T visitNamespaceCallExpr(NamespaceCallExpression expression) {
        mv.visitFieldInsn(GETSTATIC, ctx.className, "NAMESPACES", ENV_D);
        mv.visitLdcInsn(expression.getAlias());
        mv.visitLdcInsn(expression.getFunctionName());
        emitObjectArray(expression.getArguments());
        mv.visitMethodInsn(INVOKESTATIC, RT, "namespaceCall",
                "(" + ENV_D + "Ljava/lang/String;Ljava/lang/String;[" + OBJ_D + ")" + OBJ_D, false);
        return null;
    }

    @Override
    public <T> T visitLambdaExpr(LambdaExpression lambda) {
        int n = ctx.lambdaCounter[0]++;
        String methodName = "mira$lambda$" + n;
        String lambdaClass = ctx.className + "$Lambda$" + n;
        int arity = lambda.getArity();

        MethodVisitor lmv = ce.openFunction(methodName);
        lmv.visitCode();
        LocalSlotTable lSlots = new LocalSlotTable(1);
        CompilerContext lCtx = new CompilerContext(ctx.className, lmv, lSlots,
                ctx.knownFunctions, ctx.lambdaCounter, false);
        MethodEmitter lme = new MethodEmitter(lCtx, ce);

        List<Parameter> params = lambda.getParameters();
        for (int i = 0; i < params.size(); i++) {
            Parameter param = params.get(i);
            int slot = lSlots.allocate(param.name());
            if (param.hasDefault()) {
                Label useDefault = new Label(), useDefaultAfterPop = new Label(), done = new Label();
                lmv.visitVarInsn(ALOAD, 0);
                lmv.visitInsn(org.objectweb.asm.Opcodes.ARRAYLENGTH);
                emitIntConst(lmv, i + 1);
                lmv.visitJumpInsn(org.objectweb.asm.Opcodes.IF_ICMPLT, useDefault);
                lmv.visitVarInsn(ALOAD, 0);
                emitIntConst(lmv, i);
                lmv.visitInsn(AALOAD);
                lmv.visitInsn(DUP);
                lmv.visitMethodInsn(INVOKESTATIC, RT, "isNullValue", "(Ljava/lang/Object;)Z", false);
                lmv.visitJumpInsn(org.objectweb.asm.Opcodes.IFNE, useDefaultAfterPop);
                lmv.visitJumpInsn(GOTO, done);
                lmv.visitLabel(useDefaultAfterPop);
                lmv.visitInsn(POP);
                lmv.visitLabel(useDefault);
                param.defaultValue().accept(lme);
                lmv.visitLabel(done);
            } else {
                lmv.visitVarInsn(ALOAD, 0);
                emitIntConst(lmv, i);
                lmv.visitInsn(AALOAD);
            }
            lmv.visitVarInsn(ASTORE, slot);
        }

        if (lambda.getVariadicParam() != null) {
            int slot = lSlots.allocate(lambda.getVariadicParam());
            lmv.visitVarInsn(ALOAD, 0);
            emitIntConst(lmv, params.size());
            lmv.visitMethodInsn(INVOKESTATIC, RT, "variadicTail",
                    "([Ljava/lang/Object;I)Ljava/lang/Object;", false);
            lmv.visitVarInsn(ASTORE, slot);
        }

        lme.emitBody(lambda.getBody());
        lmv.visitMethodInsn(INVOKESTATIC, RT, "nullVal", "()" + OBJ_D, false);
        lmv.visitInsn(ARETURN);
        lmv.visitMaxs(0, 0);
        lmv.visitEnd();

        ce.emitLambdaClass(lambdaClass, ctx.className, methodName, arity);

        mv.visitTypeInsn(NEW, lambdaClass);
        mv.visitInsn(DUP);
        emitIntConst(arity);
        mv.visitMethodInsn(INVOKESPECIAL, lambdaClass, "<init>", "(I)V", false);
        return null;
    }

    private void emitIntConst(MethodVisitor target, int n) {
        if (n >= 0 && n <= 5) {
            target.visitInsn(ICONST_0 + n);
        } else if (n >= Byte.MIN_VALUE && n <= Byte.MAX_VALUE) {
            target.visitIntInsn(BIPUSH, n);
        } else if (n >= Short.MIN_VALUE && n <= Short.MAX_VALUE) {
            target.visitIntInsn(SIPUSH, n);
        } else {
            target.visitLdcInsn(n);
        }
    }

    @Override
    public <T> T visitArrayExpr(ArrayExpression expression) {
        int size = expression.getMembers().size();
        emitIntConst(size);
        mv.visitTypeInsn(ANEWARRAY, OBJ);
        for (int i = 0; i < size; i++) {
            mv.visitInsn(DUP);
            emitIntConst(i);
            expression.getMembers().get(i).accept(this);
            mv.visitInsn(AASTORE);
        }
        mv.visitMethodInsn(INVOKESTATIC, RT, "makeArray", "([" + OBJ_D + ")" + OBJ_D, false);
        return null;
    }

    @Override
    public <T> T visitListExpr(ListExpression expression) {
        int size = expression.getMembers().size();
        emitIntConst(size);
        mv.visitTypeInsn(ANEWARRAY, OBJ);
        for (int i = 0; i < size; i++) {
            mv.visitInsn(DUP);
            emitIntConst(i);
            expression.getMembers().get(i).accept(this);
            mv.visitInsn(AASTORE);
        }
        mv.visitMethodInsn(INVOKESTATIC, RT, "makeList", "([" + OBJ_D + ")" + OBJ_D, false);
        return null;
    }

    @Override
    public <T> T visitMapExpr(MapExpression expression) {
        int size = expression.getEntries().size() * 2;
        emitIntConst(size);
        mv.visitTypeInsn(ANEWARRAY, OBJ);
        int i = 0;
        for (var entry : expression.getEntries().entrySet()) {
            mv.visitInsn(DUP);
            emitIntConst(i++);
            mv.visitLdcInsn(entry.getKey());
            mv.visitInsn(AASTORE);
            mv.visitInsn(DUP);
            emitIntConst(i++);
            entry.getValue().accept(this);
            mv.visitInsn(AASTORE);
        }
        mv.visitMethodInsn(INVOKESTATIC, RT, "makeMap", "([" + OBJ_D + ")" + OBJ_D, false);
        return null;
    }

    @Override
    public <T> T visitAccessExpr(AccessExpression expression) {
        expression.getReference().accept(this);
        for (Expression idx : expression.getIndecies()) {
            idx.accept(this);
            mv.visitMethodInsn(INVOKESTATIC, RT, "arrayGet",
                    "(" + OBJ_D + OBJ_D + ")" + OBJ_D, false);
        }
        return null;
    }

    @Override
    public <T> T visitFieldAccessExpression(FieldAccessExpression expression) {
        expression.getObject().accept(this);
        resolveIfStringName();
        mv.visitLdcInsn(expression.getField());
        String method = expression.isOptional() ? "optionalFieldGet" : "fieldGet";
        mv.visitMethodInsn(INVOKESTATIC, RT, method,
                "(" + OBJ_D + "Ljava/lang/String;)" + OBJ_D, false);
        return null;
    }

    @Override
    public <T> T visitMethodCallExpression(MethodCallExpression expression) {
        expression.getObject().accept(this);
        resolveIfStringName();
        mv.visitLdcInsn(expression.getMethod());
        emitObjectArray(expression.getArguments());
        String method = expression.isOptional() ? "optionalMethodCall" : "methodCall";
        mv.visitMethodInsn(INVOKESTATIC, RT, method,
                "(" + OBJ_D + "Ljava/lang/String;[" + OBJ_D + ")" + OBJ_D, false);
        return null;
    }

    private void resolveIfStringName() {
        mv.visitFieldInsn(GETSTATIC, ctx.className, "NAMESPACES", ENV_D);
        mv.visitFieldInsn(GETSTATIC, ctx.className, "GLOBALS", ENV_D);
        mv.visitMethodInsn(INVOKESTATIC, RT, "resolveIfNamespace",
                "(" + OBJ_D + ENV_D + ENV_D + ")" + OBJ_D, false);
    }

    @Override
    public <T> T visitRangeExpression(RangeExpression expression) {
        expression.getStart().accept(this);
        expression.getEnd().accept(this);
        if (expression.getStepsize() != null) {
            expression.getStepsize().accept(this);
        } else {
            emitNullVal();
        }
        mv.visitMethodInsn(INVOKESTATIC, RT, "makeRange",
                "(" + OBJ_D + OBJ_D + OBJ_D + ")" + OBJ_D, false);
        return null;
    }

    @Override
    public <T> T visitObjectExpression(ObjectExpression expression) {
        mv.visitMethodInsn(INVOKESTATIC, RT, "makeObject", "()" + ENV_D, false);
        int objSlot = ctx.slots.allocate("$$obj$" + ctx.lambdaCounter[0]);
        mv.visitVarInsn(ASTORE, objSlot);

        for (VarDecl field : expression.getVarDecls()) {
            mv.visitVarInsn(ALOAD, objSlot);
            mv.visitLdcInsn(field.getName());
            if (field.getInitializer() != null) {
                field.getInitializer().accept(this);
            } else {
                emitNullVal();
            }
            String defineMethod = field.isConst() ? "defineConst" : "define";
            mv.visitMethodInsn(INVOKEVIRTUAL, ENV, defineMethod,
                    "(Ljava/lang/String;" + OBJ_D + ")V", false);
        }

        for (FuncDecl method : expression.getMethods()) {
            int n = ctx.lambdaCounter[0]++;
            String mName = "mira$lambda$" + n;
            String lClass = ctx.className + "$Lambda$" + n;
            emitMethodAsLambda(method, mName, lClass);
            mv.visitVarInsn(ALOAD, objSlot);
            mv.visitLdcInsn(method.getName());
            mv.visitTypeInsn(NEW, lClass);
            mv.visitInsn(DUP);
            emitIntConst(method.getArity());
            mv.visitMethodInsn(INVOKESPECIAL, lClass, "<init>", "(I)V", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, ENV, "define",
                    "(Ljava/lang/String;" + OBJ_D + ")V", false);
        }

        mv.visitVarInsn(ALOAD, objSlot);
        mv.visitLdcInsn("this");
        mv.visitVarInsn(ALOAD, objSlot);
        mv.visitMethodInsn(INVOKEVIRTUAL, ENV, "define",
                "(Ljava/lang/String;" + OBJ_D + ")V", false);

        mv.visitVarInsn(ALOAD, objSlot);
        return null;
    }

    private void emitMethodAsLambda(FuncDecl method, String mName, String lClass) {
        MethodVisitor lmv = ce.openFunction(mName);
        lmv.visitCode();
        LocalSlotTable lSlots = new LocalSlotTable(1);
        CompilerContext lCtx = new CompilerContext(ctx.className, lmv, lSlots,
                ctx.knownFunctions, ctx.lambdaCounter, false);
        MethodEmitter lme = new MethodEmitter(lCtx, ce);

        int objEnvSlot = lSlots.allocate("$$objEnv");
        lmv.visitFieldInsn(GETSTATIC, RT, "METHOD_ENV", "Ljava/lang/ThreadLocal;");
        lmv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/ThreadLocal", "get", "()Ljava/lang/Object;", false);
        lmv.visitTypeInsn(org.objectweb.asm.Opcodes.CHECKCAST, ENV);
        lmv.visitVarInsn(ASTORE, objEnvSlot);
        lCtx.objectEnvSlot = objEnvSlot;

        List<Parameter> params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            Parameter param = params.get(i);
            int slot = lSlots.allocate(param.name());
            if (param.hasDefault()) {
                Label useDefault = new Label(), useDefaultAfterPop = new Label(), done = new Label();
                lmv.visitVarInsn(ALOAD, 0);
                lmv.visitInsn(org.objectweb.asm.Opcodes.ARRAYLENGTH);
                emitIntConst(lmv, i + 1);
                lmv.visitJumpInsn(org.objectweb.asm.Opcodes.IF_ICMPLT, useDefault);
                lmv.visitVarInsn(ALOAD, 0);
                emitIntConst(lmv, i);
                lmv.visitInsn(AALOAD);
                lmv.visitInsn(DUP);
                lmv.visitMethodInsn(INVOKESTATIC, RT, "isNullValue", "(Ljava/lang/Object;)Z", false);
                lmv.visitJumpInsn(org.objectweb.asm.Opcodes.IFNE, useDefaultAfterPop);
                lmv.visitJumpInsn(GOTO, done);
                lmv.visitLabel(useDefaultAfterPop);
                lmv.visitInsn(POP);
                lmv.visitLabel(useDefault);
                param.defaultValue().accept(lme);
                lmv.visitLabel(done);
            } else {
                lmv.visitVarInsn(ALOAD, 0);
                emitIntConst(lmv, i);
                lmv.visitInsn(AALOAD);
            }
            lmv.visitVarInsn(ASTORE, slot);
        }
        lme.emitBody(method.getBody());
        lmv.visitMethodInsn(INVOKESTATIC, RT, "nullVal", "()" + OBJ_D, false);
        lmv.visitInsn(ARETURN);
        lmv.visitMaxs(0, 0);
        lmv.visitEnd();
        ce.emitLambdaClass(lClass, ctx.className, mName, method.getArity());
    }

    @Override
    public <T> T visitComplexExpr(ComplexExpression expression) {
        List<Expression> parts = expression.getExpressions();
        if (parts.isEmpty()) {
            mv.visitLdcInsn("");
            return null;
        }
        parts.get(0).accept(this);
        for (int i = 1; i < parts.size(); i++) {
            parts.get(i).accept(this);
            mv.visitMethodInsn(INVOKESTATIC, RT, "add",
                    "(" + OBJ_D + OBJ_D + ")" + OBJ_D, false);
        }
        return null;
    }

    @Override
    public <T> T visitThrownExpection(ThrownException expression) {
        if (expression.getValue() != null) {
            expression.getValue().accept(this);
        } else {
            emitNullVal();
        }
        return null;
    }

    @Override
    public <T> T visitAwaitExpr(AwaitExpression expression) {
        expression.getExpr().accept(this);
        mv.visitMethodInsn(INVOKESTATIC, RT, "awaitPromise",
                "(" + OBJ_D + ")" + OBJ_D, false);
        return null;
    }

    @Override
    public Void visitVarDecl(VarDecl stmt) {
        if (stmt.getInitializer() != null) {
            stmt.getInitializer().accept(this);
        } else {
            emitNullVal();
        }
        emitVarStore(stmt.getName(), true);
        return null;
    }

    @Override
    public Void visitFuncDecl(FuncDecl stmt) {
        if (ctx.isTopLevel) {
            return null;
        }
        int n = ctx.lambdaCounter[0]++;
        String mName = "mira$lambda$" + n;
        String lClass = ctx.className + "$Lambda$" + n;

        MethodVisitor lmv = ce.openFunction(mName);
        lmv.visitCode();
        LocalSlotTable lSlots = new LocalSlotTable(1);
        CompilerContext lCtx = new CompilerContext(ctx.className, lmv, lSlots,
                ctx.knownFunctions, ctx.lambdaCounter, false);
        MethodEmitter lme = new MethodEmitter(lCtx, ce);
        List<Parameter> params = stmt.getParameters();
        for (int i = 0; i < params.size(); i++) {
            int slot = lSlots.allocate(params.get(i).name());
            lmv.visitVarInsn(ALOAD, 0);
            emitIntConst(lmv, i);
            lmv.visitInsn(AALOAD);
            lmv.visitVarInsn(ASTORE, slot);
        }
        lme.emitBody(stmt.getBody());
        lmv.visitMethodInsn(INVOKESTATIC, RT, "nullVal", "()" + OBJ_D, false);
        lmv.visitInsn(ARETURN);
        lmv.visitMaxs(0, 0);
        lmv.visitEnd();
        ce.emitLambdaClass(lClass, ctx.className, mName, stmt.getArity());

        mv.visitTypeInsn(NEW, lClass);
        mv.visitInsn(DUP);
        emitIntConst(stmt.getArity());
        mv.visitMethodInsn(INVOKESPECIAL, lClass, "<init>", "(I)V", false);
        emitVarStore(stmt.getName(), true);
        return null;
    }

    @Override
    public Void visitAssign(Assign assign) {
        switch (assign.getReference()) {
            case UnaryExpression ue when ue.getOperation().getLexeme().equals("$") -> {
                String name = ((DumbExpression) ue.getRight()).getValue();
                Integer slot = ctx.slots.slotOf(name);
                if (!ctx.isTopLevel && slot != null) {
                    assign.getExpression().accept(this);
                    mv.visitVarInsn(ASTORE, slot);
                } else {
                    emitGlobals();
                    mv.visitLdcInsn(name);
                    assign.getExpression().accept(this);
                    mv.visitMethodInsn(INVOKEVIRTUAL, ENV, "assign",
                            "(Ljava/lang/String;" + OBJ_D + ")V", false);
                }
            }
            case AccessExpression acc -> {
                acc.getReference().accept(this);
                for (int k = 0; k < acc.getIndecies().size() - 1; k++) {
                    acc.getIndecies().get(k).accept(this);
                    mv.visitMethodInsn(INVOKESTATIC, RT, "arrayGet",
                            "(" + OBJ_D + OBJ_D + ")" + OBJ_D, false);
                }
                acc.getIndecies().getLast().accept(this);
                assign.getExpression().accept(this);
                mv.visitMethodInsn(INVOKESTATIC, RT, "arraySet",
                        "(" + OBJ_D + OBJ_D + OBJ_D + ")V", false);
            }
            case FieldAccessExpression fae -> {
                fae.getObject().accept(this);
                mv.visitLdcInsn(fae.getField());
                assign.getExpression().accept(this);
                mv.visitMethodInsn(INVOKESTATIC, RT, "fieldSet",
                        "(" + OBJ_D + "Ljava/lang/String;" + OBJ_D + ")V", false);
            }
            default ->
                throw new RuntimeException("Unsupported assign target: " + assign.getReference());
        }
        return null;
    }

    @Override
    public Void visitReturn(Return ret) {
        if (ret.getValue() != null) {
            ret.getValue().accept(this);
        } else {
            emitNullVal();
        }
        if (ctx.isTopLevel) {
            String sig = "com/mira/runtime/functions/ReturnSignal";
            int tmpSlot = ctx.slots.allocateTemp();
            mv.visitVarInsn(ASTORE, tmpSlot);
            mv.visitTypeInsn(NEW, sig);
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, tmpSlot);
            mv.visitMethodInsn(INVOKESPECIAL, sig, "<init>", "(Ljava/lang/Object;)V", false);
            mv.visitInsn(ATHROW);
        } else {
            mv.visitInsn(ARETURN);
        }
        return null;
    }

    @Override
    public Void visitIf(If stmt) {
        Label elseLabel = new Label(), end = new Label();
        stmt.getCondition().accept(this);
        emitIsTruthy();
        mv.visitJumpInsn(IFEQ, elseLabel);
        ctx.slots.enterScope();
        emitBody(stmt.getThenBody());
        ctx.slots.exitScope();
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(elseLabel);
        if (stmt.getElseBody() != null && !stmt.getElseBody().isEmpty()) {
            ctx.slots.enterScope();
            emitBody(stmt.getElseBody());
            ctx.slots.exitScope();
        }
        mv.visitLabel(end);
        return null;
    }

    @Override
    public Void visitFor(For stmt) {
        ctx.slots.enterScope();
        emitBody(stmt.getVarDecls());

        Label forTest = new Label(), forEnd = new Label(), forPost = new Label();
        ctx.breakStack.push(forEnd);
        ctx.continueStack.push(forPost);

        mv.visitLabel(forTest);
        if (stmt.getCondition() != null) {
            stmt.getCondition().accept(this);
            emitIsTruthy();
            mv.visitJumpInsn(IFEQ, forEnd);
        }

        ctx.slots.enterScope();
        emitBody(stmt.getBody());
        ctx.slots.exitScope();

        mv.visitLabel(forPost);
        emitBody(stmt.getPostExpressions());
        mv.visitJumpInsn(GOTO, forTest);
        mv.visitLabel(forEnd);

        ctx.breakStack.pop();
        ctx.continueStack.pop();
        ctx.slots.exitScope();
        return null;
    }

    @Override
    public Void visitWhile(While stmt) {
        Label loopStart = new Label(), loopEnd = new Label();
        ctx.breakStack.push(loopEnd);
        ctx.continueStack.push(loopStart);

        if (!stmt.getDoModifier()) {
            mv.visitLabel(loopStart);
            stmt.getCondition().accept(this);
            emitIsTruthy();
            mv.visitJumpInsn(IFEQ, loopEnd);
            ctx.slots.enterScope();
            emitBody(stmt.getBody());
            ctx.slots.exitScope();
            mv.visitJumpInsn(GOTO, loopStart);
        } else {
            mv.visitLabel(loopStart);
            ctx.slots.enterScope();
            emitBody(stmt.getBody());
            ctx.slots.exitScope();
            stmt.getCondition().accept(this);
            emitIsTruthy();
            mv.visitJumpInsn(IFNE, loopStart);
        }

        mv.visitLabel(loopEnd);
        ctx.breakStack.pop();
        ctx.continueStack.pop();
        return null;
    }

    @Override
    public Void visitForeach(Foreach stmt) {
        String iterName = stmt.getIterator().getName();
        Label loopEnd = new Label(), continueLabel = new Label();
        ctx.breakStack.push(loopEnd);
        ctx.continueStack.push(continueLabel);

        if (stmt.getCollection() instanceof RangeExpression range) {
            range.getStart().accept(this);
            int iterSlot = ctx.slots.allocate(iterName);
            mv.visitVarInsn(ASTORE, iterSlot);
            range.getEnd().accept(this);
            int endSlot = ctx.slots.allocate("$$end$" + iterName);
            mv.visitVarInsn(ASTORE, endSlot);
            Object stepVal = range.getStepsize();
            int stepSlot = ctx.slots.allocate("$$step$" + iterName);
            if (stepVal != null) {
                ((Expression) stepVal).accept(this);
            } else {
                mv.visitLdcInsn(1L);
                mv.visitMethodInsn(INVOKESTATIC, RT, "wrapLong", "(J)" + OBJ_D, false);
            }
            mv.visitVarInsn(ASTORE, stepSlot);

            Label loopTop = new Label();
            mv.visitLabel(loopTop);
            mv.visitVarInsn(ALOAD, iterSlot);
            mv.visitVarInsn(ALOAD, endSlot);
            mv.visitMethodInsn(INVOKESTATIC, RT, "lt",
                    "(" + OBJ_D + OBJ_D + ")" + OBJ_D, false);
            emitIsTruthy();
            mv.visitJumpInsn(IFEQ, loopEnd);

            ctx.slots.enterScope();
            emitBody(stmt.getBody());
            ctx.slots.exitScope();

            mv.visitLabel(continueLabel);
            mv.visitVarInsn(ALOAD, iterSlot);
            mv.visitVarInsn(ALOAD, stepSlot);
            mv.visitMethodInsn(INVOKESTATIC, RT, "add",
                    "(" + OBJ_D + OBJ_D + ")" + OBJ_D, false);
            mv.visitVarInsn(ASTORE, iterSlot);
            mv.visitJumpInsn(GOTO, loopTop);

        } else {
            stmt.getCollection().accept(this);
            int collSlot = ctx.slots.allocate("$$coll$" + iterName);
            mv.visitVarInsn(ASTORE, collSlot);

            int sizeSlot = ctx.slots.allocate("$$size$" + iterName);
            mv.visitVarInsn(ALOAD, collSlot);
            mv.visitMethodInsn(INVOKESTATIC, RT, "collectionSize", "(" + OBJ_D + ")I", false);
            mv.visitVarInsn(ISTORE, sizeSlot);

            int idxSlot = ctx.slots.allocate("$$idx$" + iterName);
            mv.visitInsn(ICONST_0);
            mv.visitVarInsn(ISTORE, idxSlot);

            int iterSlot = ctx.slots.allocate(iterName);

            Label loopTop = new Label();
            mv.visitLabel(loopTop);
            mv.visitVarInsn(ILOAD, idxSlot);
            mv.visitVarInsn(ILOAD, sizeSlot);
            mv.visitJumpInsn(IF_ICMPGE, loopEnd);

            mv.visitVarInsn(ALOAD, collSlot);
            mv.visitVarInsn(ILOAD, idxSlot);
            mv.visitMethodInsn(INVOKESTATIC, RT, "collectionGet", "(" + OBJ_D + "I)" + OBJ_D, false);
            mv.visitVarInsn(ASTORE, iterSlot);

            ctx.slots.enterScope();
            emitBody(stmt.getBody());
            ctx.slots.exitScope();

            mv.visitLabel(continueLabel);
            mv.visitIincInsn(idxSlot, 1);
            mv.visitJumpInsn(GOTO, loopTop);
        }

        mv.visitLabel(loopEnd);
        ctx.breakStack.pop();
        ctx.continueStack.pop();
        return null;
    }

    @Override
    public Void visitBreak(Break stmt) {
        if (ctx.breakStack.isEmpty()) {
            throw new RuntimeException("break outside loop");
        }
        mv.visitJumpInsn(GOTO, ctx.breakStack.peek());
        return null;
    }

    @Override
    public Void visitContinue(Continue stmt) {
        if (ctx.continueStack.isEmpty()) {
            throw new RuntimeException("continue outside loop");
        }
        mv.visitJumpInsn(GOTO, ctx.continueStack.peek());
        return null;
    }

    @Override
    public Void visitBlock(Block stmt) {
        ctx.slots.enterScope();
        ctx.blockDepth++;
        emitBody(stmt.getBody());
        ctx.blockDepth--;
        ctx.slots.exitScope();
        return null;
    }

    @Override
    public Void visitSwitch(Switch stmt) {
        int subjSlot = ctx.slots.allocate("$$switch");
        stmt.getSubject().accept(this);
        mv.visitVarInsn(ASTORE, subjSlot);

        Label switchEnd = new Label();
        ctx.breakStack.push(switchEnd);

        for (Statement.SwitchCase sc : stmt.getCases()) {
            Label skip = new Label();
            mv.visitVarInsn(ALOAD, subjSlot);
            sc.getValue().accept(this);
            mv.visitMethodInsn(INVOKESTATIC, RT, "eq",
                    "(" + OBJ_D + OBJ_D + ")" + OBJ_D, false);
            emitIsTruthy();
            mv.visitJumpInsn(IFEQ, skip);
            ctx.slots.enterScope();
            emitBody(sc.getBody());
            ctx.slots.exitScope();
            mv.visitJumpInsn(GOTO, switchEnd);
            mv.visitLabel(skip);
        }
        if (stmt.getDefaultBody() != null) {
            ctx.slots.enterScope();
            emitBody(stmt.getDefaultBody());
            ctx.slots.exitScope();
        }

        mv.visitLabel(switchEnd);
        ctx.breakStack.pop();
        return null;
    }

    @Override
    public Void visitTryCatch(TryCatch stmt) {
        Label tryStart = new Label(), tryEnd = new Label(),
                catchStart = new Label(), afterCatch = new Label();

        mv.visitLabel(tryStart);
        ctx.slots.enterScope();
        emitBody(stmt.getTryBody());
        ctx.slots.exitScope();
        mv.visitLabel(tryEnd);

        mv.visitTryCatchBlock(tryStart, tryEnd, catchStart,
                ClassEmitter.THROW_NAME);

        mv.visitJumpInsn(GOTO, afterCatch);

        mv.visitLabel(catchStart);
        int sigSlot = ctx.slots.allocate("$$throwSignal");
        mv.visitVarInsn(ASTORE, sigSlot);

        for (Statement.CatchClause clause : stmt.getCatchClauses()) {
            Label nextClause = new Label();
            String filter = clause.getTypeFilter();
            if (filter != null) {
                mv.visitVarInsn(ALOAD, sigSlot);
                mv.visitMethodInsn(INVOKEVIRTUAL, ClassEmitter.THROW_NAME,
                        "getExceptionType", "()Ljava/lang/String;", false);
                mv.visitLdcInsn(filter);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals",
                        "(Ljava/lang/Object;)Z", false);
                mv.visitJumpInsn(IFEQ, nextClause);
            }
            ctx.slots.enterScope();
            if (clause.getParamName() != null) {
                mv.visitVarInsn(ALOAD, sigSlot);
                mv.visitMethodInsn(INVOKEVIRTUAL, ClassEmitter.THROW_NAME,
                        "getValue", "()" + OBJ_D, false);
                int paramSlot = ctx.slots.allocate(clause.getParamName());
                mv.visitVarInsn(ASTORE, paramSlot);
            }
            emitBody(clause.getBody());
            ctx.slots.exitScope();
            mv.visitJumpInsn(GOTO, afterCatch);
            mv.visitLabel(nextClause);
        }
        mv.visitVarInsn(ALOAD, sigSlot);
        mv.visitInsn(ATHROW);

        mv.visitLabel(afterCatch);
        if (!stmt.getFinallyBody().isEmpty()) {
            ctx.slots.enterScope();
            emitBody(stmt.getFinallyBody());
            ctx.slots.exitScope();
        }
        return null;
    }

    @Override
    public Void visitThrow(Throw stmt) {
        ThrownException tex = (ThrownException) stmt.getValue();
        mv.visitLdcInsn(tex.getIdentifier() != null ? tex.getIdentifier() : "");
        if (tex.getValue() != null) {
            tex.getValue().accept(this);
        } else {
            emitNullVal();
        }
        mv.visitMethodInsn(INVOKESTATIC, RT, "makeThrow",
                "(Ljava/lang/String;" + OBJ_D + ")"
                + "Lcom/mira/runtime/functions/ThrowSignal;", false);
        mv.visitInsn(ATHROW);
        return null;
    }

    @Override
    public Void visitEnum(EnumDecl stmt) {
        String[] keys = stmt.getValues().keySet().toArray(String[]::new);
        emitIntConst(keys.length);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
        for (int i = 0; i < keys.length; i++) {
            mv.visitInsn(DUP);
            emitIntConst(i);
            mv.visitLdcInsn(keys[i]);
            mv.visitInsn(AASTORE);
        }

        emitIntConst(keys.length);
        mv.visitTypeInsn(ANEWARRAY, OBJ);
        Object[] vals = stmt.getValues().values().toArray();
        for (int i = 0; i < vals.length; i++) {
            mv.visitInsn(DUP);
            emitIntConst(i);
            emitLiteral(vals[i]);
            mv.visitInsn(AASTORE);
        }
        mv.visitMethodInsn(INVOKESTATIC, RT, "makeEnum",
                "([Ljava/lang/String;[" + OBJ_D + ")" + ENV_D, false);

        int enumSlot = ctx.slots.allocate("$$enum");
        mv.visitVarInsn(ASTORE, enumSlot);
        emitGlobals();
        mv.visitLdcInsn(stmt.getIdentifier());
        mv.visitVarInsn(ALOAD, enumSlot);
        mv.visitMethodInsn(INVOKEVIRTUAL, ENV, "defineConst",
                "(Ljava/lang/String;" + OBJ_D + ")V", false);
        return null;
    }

    private void emitLiteral(Object val) {
        if (val instanceof Long l) {
            mv.visitLdcInsn(l);
            mv.visitMethodInsn(INVOKESTATIC, RT, "wrapLong", "(J)" + OBJ_D, false);
        } else if (val instanceof Double d) {
            mv.visitLdcInsn(d);
            mv.visitMethodInsn(INVOKESTATIC, RT, "wrapDouble", "(D)" + OBJ_D, false);
        } else if (val instanceof String s) {
            mv.visitLdcInsn(s);
        } else if (val instanceof Boolean b) {
            mv.visitInsn(b ? ICONST_1 : ICONST_0);
            mv.visitMethodInsn(INVOKESTATIC, RT, "wrapBool", "(Z)" + OBJ_D, false);
        } else {
            emitNullVal();
        }
    }

    @Override
    public Void visitVarDestructure(VarDestructure stmt) {
        stmt.getInitializer().accept(this);
        int collSlot = ctx.slots.allocate("$$destr");
        mv.visitVarInsn(ASTORE, collSlot);
        List<String> names = stmt.getNames();
        for (int i = 0; i < names.size(); i++) {
            mv.visitVarInsn(ALOAD, collSlot);
            mv.visitLdcInsn((long) i);
            mv.visitMethodInsn(INVOKESTATIC, RT, "wrapLong", "(J)" + OBJ_D, false);
            mv.visitMethodInsn(INVOKESTATIC, RT, "safeArrayGet",
                    "(" + OBJ_D + OBJ_D + ")" + OBJ_D, false);
            emitVarStore(names.get(i), true);
        }
        return null;
    }

    @Override
    public Void visitOverwrite(Overwrite stmt) {
        throw new UnsupportedOperationException("overwrite is not supported in compiled mode");
    }
}
