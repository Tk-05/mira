package com.mira.runtime.visitors;

import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.Continue;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.StaticAssert;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.TestCall;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;

public interface StmtVisitor<T> {

    public T visitVarDecl(VarDecl stmt);

    public T visitFuncDecl(FuncDecl stmt);

    public T visitReturn(Return stmt);

    public T visitAssign(Assign stmt);

    public T visitIf(If stmt);

    public T visitLoop(Loop stmt);

    public T visitWhile(While stmt);

    public T visitBreak(Break stmt);

    public T visitContinue(Continue stmt);

    public T visitBlock(Block stmt);

    public T visitSwitch(Switch stmt);

    public T visitEnum(EnumDecl stmt);

    public T visitThrow(Throw stmt);

    public T visitTryCatch(TryCatch stmt);

    public T visitVarDestructure(VarDestructure stmt);

    public T visitLock(Lock stmt);

    default T visitComptimeBlock(ComptimeBlock stmt) {
        return null;
    }

    default T visitTestCall(TestCall stmt) {
        return null;
    }

    default T visitStaticAssert(StaticAssert stmt) {
        return null;
    }
}
