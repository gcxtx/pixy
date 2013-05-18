package at.ac.tuwien.infosys.www.pixy;

import at.ac.tuwien.infosys.www.phpparser.ParseNode;
import at.ac.tuwien.infosys.www.phpparser.ParseTree;
import at.ac.tuwien.infosys.www.pixy.analysis.AnalysisNode;
import at.ac.tuwien.infosys.www.pixy.analysis.LatticeElement;
import at.ac.tuwien.infosys.www.pixy.analysis.LatticeElementBottom;
import at.ac.tuwien.infosys.www.pixy.analysis.alias.*;
import at.ac.tuwien.infosys.www.pixy.analysis.dep.DepLatticeElement;
import at.ac.tuwien.infosys.www.pixy.analysis.dep.DepSet;
import at.ac.tuwien.infosys.www.pixy.analysis.incdom.IncDomAnalysis;
import at.ac.tuwien.infosys.www.pixy.analysis.incdom.IncDomLatticeElement;
import at.ac.tuwien.infosys.www.pixy.analysis.inter.InterAnalysis;
import at.ac.tuwien.infosys.www.pixy.analysis.inter.InterAnalysisInfo;
import at.ac.tuwien.infosys.www.pixy.analysis.inter.InterAnalysisNode;
import at.ac.tuwien.infosys.www.pixy.analysis.inter.callstring.ECS;
import at.ac.tuwien.infosys.www.pixy.analysis.intra.IntraAnalysisNode;
import at.ac.tuwien.infosys.www.pixy.analysis.literal.DummyLiteralAnalysis;
import at.ac.tuwien.infosys.www.pixy.analysis.literal.LiteralAnalysis;
import at.ac.tuwien.infosys.www.pixy.analysis.literal.LiteralLatticeElement;
import at.ac.tuwien.infosys.www.pixy.conversion.*;
import at.ac.tuwien.infosys.www.pixy.conversion.nodes.*;

import java.io.*;
import java.util.*;

/**
 * @author Nenad Jovanovic <enji@seclab.tuwien.ac.at>
 */
public final class Dumper {

    // auxiliary HashMap: CfgNode -> Integer
    private static HashMap<CfgNode, Integer> node2Int;
    private static int idCounter;
    static final String linesep = System.getProperty("line.separator");

// *********************************************************************************
// CONSTRUCTORS ********************************************************************
// *********************************************************************************

    // since this class is stateless, there is no need to create an instance of it
    private Dumper() {
    }

// *********************************************************************************
// DOT: ParseTree ******************************************************************
// *********************************************************************************

// dumpDot(ParseTree, String, String) **********************************************

    // dumps the parse tree in dot syntax to the directory specified
    // by "path" and the file specified by "filename"
    static void dumpDot(ParseTree parseTree, String path, String filename) {

        // create directory
        (new File(path)).mkdir();

        try {
            Writer outWriter = new FileWriter(path + '/' + filename);
            dumpDot(parseTree, outWriter);
            outWriter.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

// dumpDot(ParseTree, String, Writer) **********************************************

    // dumps the parse tree in dot syntax using the specified Writer
    static void dumpDot(ParseTree parseTree, Writer outWriter) {
        try {
            outWriter.write("digraph parse_tree {\n");
            dumpDot(parseTree.getRoot(), outWriter);
            outWriter.write("}\n");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

// dumpDot(ParseNode, Writer) ******************************************************

    // dumps the subtree starting at the given parse node in dot syntax
    static void dumpDot(ParseNode parseNode, Writer outWriter)
        throws java.io.IOException {

        outWriter.write("  n" + parseNode.getId() + " [label=\"");

        // print symbol
        String symbolName = parseNode.getName();
        outWriter.write(escapeDot(symbolName, 0));

        // print lexeme for token nodes
        if (parseNode.isToken()) {
            String lexeme = parseNode.getLexeme();
            outWriter.write("\\n");
            outWriter.write(escapeDot(lexeme, 10));
        }
        outWriter.write("\"];\n");

        // print edge to parent
        ParseNode parent = parseNode.getParent();
        if (parent != null) {
            outWriter.write("  n" + parent.getId() + " -> n" +
                parseNode.getId() + ";\n");
        }
        // recursion
        for (int i = 0; i < parseNode.getChildren().size(); i++) {
            dumpDot(parseNode.getChild(i), outWriter);
        }
    }

// *********************************************************************************
// DOT: TacFunction ****************************************************************
// *********************************************************************************

// dumpDot(TacFunction, String, boolean) *******************************************

    // dumps the function's Cfg in dot syntax
    public static void dumpDot(TacFunction function, String graphPath, boolean dumpParams) {
        dumpDot(function.getCfg(), function.getName(), graphPath);

        if (dumpParams) {
            for (TacFormalParam parameter : function.getParams()) {
                String paramString = parameter.getVariable().getName();
                paramString = paramString.substring(1); // remove "$"
                if (parameter.hasDefault()) {
                    dumpDot(
                        parameter.getDefaultCfg(),
                        function.getName() + "_" + paramString,
                        graphPath);
                }
            }
        }
    }

// dumpDot(Cfg, String) ************************************************************

    static void dumpDot(Cfg cfg, String graphName, String graphPath) {
        dumpDot(cfg, graphName, graphPath, graphName + ".dot");
    }

// dumpDot(Cfg, String, String, String) ********************************************

    // dumps the Cfg in dot syntax to the directory specified by "path" and the
    // file specified by "filename"
    public static void dumpDot(Cfg cfg, String graphName, String path, String filename) {

        // create directory
        (new File(path)).mkdir();

        try {
            Writer outWriter = new FileWriter(path + "/" + filename);
            dumpDot(cfg, graphName, outWriter);
            outWriter.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

// dumpDot(Cfg, String, Writer) ****************************************************

    // dumps the Cfg in dot syntax using the specified Writer
    static void dumpDot(Cfg cfg, String graphName, Writer outWriter) {

        try {
            Dumper.node2Int = new HashMap<>();
            Dumper.idCounter = 0;
            outWriter.write("digraph cfg {\n  label=\"");
            outWriter.write(escapeDot(graphName, 0));
            outWriter.write("\";\n");
            outWriter.write("  labelloc=t;\n");
            dumpDot(cfg.getHead(), outWriter);
            outWriter.write("}\n");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

// dumpDot(CfgNode) ****************************************************************

    // recursively dumps the CfgNode in dot syntax
    // and returns the ID that is assigned to this node
    static int dumpDot(CfgNode cfgNode, Writer outWriter)
        throws java.io.IOException {

        // mark node as visited and store ID
        int nodeId = Dumper.idCounter;
        Dumper.node2Int.put(cfgNode, Dumper.idCounter++);

        // print node
        String name = makeCfgNodeName(cfgNode);
        outWriter.write("  n" + nodeId + " [label=\"" + name + "\"];\n");

        // handle successors
        int succId;
        for (int i = 0; i < 2; i++) {

            CfgEdge outEdge = cfgNode.getOutEdge(i);

            if (outEdge != null) {

                CfgNode succNode = outEdge.getDest();

                // print successor
                Integer succIdInt = Dumper.node2Int.get(succNode);
                if (succIdInt == null) {
                    succId = dumpDot(succNode, outWriter);
                } else {
                    succId = succIdInt;
                }

                // print edge to successor
                outWriter.write("  n" + nodeId + " -> n" + succId);
                if (outEdge.getType() != CfgEdge.NORMAL_EDGE) {
                    outWriter.write(" [label=\"" + outEdge.getName() + "\"]");
                }
                outWriter.write(";\n");
            }
        }

        return nodeId;
    }

// *********************************************************************************
// OTHER ***************************************************************************
// *********************************************************************************

// dump(TacFunction) ***************************************************************

    // dumps function information
    public static void dump(TacFunction function) {
        System.out.println("***************************************");
        System.out.println("Function " + function.getName());
        System.out.println("***************************************");
        System.out.println();
        if (function.isReference()) {
            System.out.println("isReference");
        }
        for (TacFormalParam param : function.getParams()) {
            String paramString = param.getVariable().getName();
            System.out.print("Param: " + paramString);
            if (param.isReference()) {
                System.out.print(" (isReference)");
            }
            if (param.hasDefault()) {
                System.out.print(" (hasDefault)");
            }
            System.out.println();
        }
    }

// makeCfgNodeName(CfgNode) ********************************************************

    // creates a string representation for the given cfg node
    public static String makeCfgNodeName(CfgNode cfgNodeX) {

        if (cfgNodeX instanceof CfgNodeBasicBlock) {

            CfgNodeBasicBlock cfgNode = (CfgNodeBasicBlock) cfgNodeX;
            StringBuilder label = new StringBuilder("basic block\\n");
            for (CfgNode containedNode : cfgNode.getContainedNodes()) {
                label.append(makeCfgNodeName(containedNode));
                label.append("\\n");
            }
            return label.toString();
        } else if (cfgNodeX instanceof CfgNodeAssignSimple) {

            CfgNodeAssignSimple cfgNode = (CfgNodeAssignSimple) cfgNodeX;
            String leftString = getPlaceString(cfgNode.getLeft());
            String rightString = getPlaceString(cfgNode.getRight());
            return (leftString + " = " + rightString);
        } else if (cfgNodeX instanceof CfgNodeAssignBinary) {

            CfgNodeAssignBinary cfgNode = (CfgNodeAssignBinary) cfgNodeX;
            String leftString = getPlaceString(cfgNode.getLeft());
            String leftOperandString = getPlaceString(cfgNode.getLeftOperand());
            String rightOperandString = getPlaceString(cfgNode.getRightOperand());
            int op = cfgNode.getOperator();

            return (
                leftString +
                    " = " +
                    leftOperandString +
                    " " + TacOperators.opToName(op) + " " +
                    rightOperandString);
        } else if (cfgNodeX instanceof CfgNodeAssignUnary) {

            CfgNodeAssignUnary cfgNode = (CfgNodeAssignUnary) cfgNodeX;
            String leftString = getPlaceString(cfgNode.getLeft());
            String rightString = getPlaceString(cfgNode.getRight());
            int op = cfgNode.getOperator();

            return (
                leftString +
                    " = " +
                    " " + TacOperators.opToName(op) + " " +
                    rightString);
        } else if (cfgNodeX instanceof CfgNodeAssignRef) {

            CfgNodeAssignRef cfgNode = (CfgNodeAssignRef) cfgNodeX;
            String leftString = getPlaceString(cfgNode.getLeft());
            String rightString = getPlaceString(cfgNode.getRight());
            return (leftString + " =& " + rightString);
        } else if (cfgNodeX instanceof CfgNodeIf) {

            CfgNodeIf cfgNode = (CfgNodeIf) cfgNodeX;
            String leftOperandString = getPlaceString(cfgNode.getLeftOperand());
            String rightOperandString = getPlaceString(cfgNode.getRightOperand());
            int op = cfgNode.getOperator();

            return (
                "if " +
                    leftOperandString +
                    " " + TacOperators.opToName(op) + " " +
                    rightOperandString);
        } else if (cfgNodeX instanceof CfgNodeEmpty) {
            return ";";
        } else if (cfgNodeX instanceof CfgNodeEntry) {
            return "entry";
        } else if (cfgNodeX instanceof CfgNodeExit) {
            CfgNodeExit cfgNode = (CfgNodeExit) cfgNodeX;
            return "exit " + cfgNode.getEnclosingFunction().getName();
        } else if (cfgNodeX instanceof CfgNodeCall) {
            CfgNodeCall cfgNode = (CfgNodeCall) cfgNodeX;
            String objectString = "";
            Variable object = cfgNode.getObject();
            if (object != null) {
                objectString = object + "->";
            }
            return (objectString + cfgNode.getFunctionNamePlace().toString() + "(...)");
        } else if (cfgNodeX instanceof CfgNodeCallPrep) {

            CfgNodeCallPrep cfgNode = (CfgNodeCallPrep) cfgNodeX;

            // construct parameter list
            List<TacActualParam> paramList = cfgNode.getParamList();
            StringBuilder paramListStringBuf = new StringBuilder();
            for (Iterator<TacActualParam> iter = paramList.iterator(); iter.hasNext(); ) {
                TacActualParam param = iter.next();
                if (param.isReference()) {
                    paramListStringBuf.append("&");
                }
                paramListStringBuf.append(getPlaceString(param.getPlace()));
                if (iter.hasNext()) {
                    paramListStringBuf.append(", ");
                }
            }

            return (
                "prepare " +
                    cfgNode.getFunctionNamePlace().toString() + "(" +
                    paramListStringBuf.toString() + ")");
        } else if (cfgNodeX instanceof CfgNodeCallRet) {

            CfgNodeCallRet cfgNode = (CfgNodeCallRet) cfgNodeX;
            return ("call-return (" + cfgNode.getTempVar() + ")");
        } else if (cfgNodeX instanceof CfgNodeCallBuiltin) {

            CfgNodeCallBuiltin cfgNode = (CfgNodeCallBuiltin) cfgNodeX;

            // construct parameter list
            List<TacActualParam> paramList = cfgNode.getParamList();
            StringBuilder paramListStringBuf = new StringBuilder();
            for (Iterator<TacActualParam> iter = paramList.iterator(); iter.hasNext(); ) {
                TacActualParam param = iter.next();
                if (param.isReference()) {
                    paramListStringBuf.append("&");
                }
                paramListStringBuf.append(getPlaceString(param.getPlace()));
                if (iter.hasNext()) {
                    paramListStringBuf.append(", ");
                }
            }

            return (
                cfgNode.getFunctionName() + "(" +
                    paramListStringBuf.toString() + ") " + "<" +
                    getPlaceString(cfgNode.getTempVar()) + ">");
        } else if (cfgNodeX instanceof CfgNodeCallUnknown) {

            CfgNodeCallUnknown cfgNode = (CfgNodeCallUnknown) cfgNodeX;

            // construct parameter list
            List<TacActualParam> paramList = cfgNode.getParamList();
            StringBuilder paramListStringBuf = new StringBuilder();
            for (Iterator<TacActualParam> iter = paramList.iterator(); iter.hasNext(); ) {
                TacActualParam param = iter.next();
                if (param.isReference()) {
                    paramListStringBuf.append("&");
                }
                paramListStringBuf.append(getPlaceString(param.getPlace()));
                if (iter.hasNext()) {
                    paramListStringBuf.append(", ");
                }
            }

            return ("UNKNOWN: " +
                cfgNode.getFunctionName() + "(" +
                paramListStringBuf.toString() + ") " + "<" +
                getPlaceString(cfgNode.getTempVar()) + ">");
        } else if (cfgNodeX instanceof CfgNodeAssignArray) {

            CfgNodeAssignArray cfgNode = (CfgNodeAssignArray) cfgNodeX;
            String leftString = getPlaceString(cfgNode.getLeft());
            return (leftString + " = array()");
        } else if (cfgNodeX instanceof CfgNodeUnset) {

            CfgNodeUnset cfgNode = (CfgNodeUnset) cfgNodeX;
            String unsetMe = cfgNode.getOperand().getVariable().toString();
            return ("unset(" + unsetMe + ")");
        } else if (cfgNodeX instanceof CfgNodeEcho) {

            CfgNodeEcho cfgNode = (CfgNodeEcho) cfgNodeX;
            String echoMe = getPlaceString(cfgNode.getPlace());
            return ("echo(" + echoMe + ")");
        } else if (cfgNodeX instanceof CfgNodeGlobal) {

            CfgNodeGlobal cfgNode = (CfgNodeGlobal) cfgNodeX;
            String globMe = cfgNode.getOperand().toString();
            return ("global " + globMe);
        } else if (cfgNodeX instanceof CfgNodeStatic) {

            CfgNodeStatic cfgNode = (CfgNodeStatic) cfgNodeX;
            String statMe = cfgNode.getOperand().getVariable().toString();
            String initial;
            if (cfgNode.hasInitialPlace()) {
                initial = " = " + getPlaceString(cfgNode.getInitialPlace());
            } else {
                initial = "";
            }
            return ("static " + statMe + initial);
        } else if (cfgNodeX instanceof CfgNodeIsset) {

            CfgNodeIsset cfgNode = (CfgNodeIsset) cfgNodeX;
            String checkMe = cfgNode.getRight().getVariable().toString();
            String leftString = cfgNode.getLeft().getVariable().toString();
            return (leftString + " = " + "isset(" + checkMe + ")");
        } else if (cfgNodeX instanceof CfgNodeEmptyTest) {

            CfgNodeEmptyTest cfgNode = (CfgNodeEmptyTest) cfgNodeX;
            String checkMe = cfgNode.getRight().getVariable().toString();
            String leftString = cfgNode.getLeft().getVariable().toString();
            return (leftString + " = " + "empty(" + checkMe + ")");
        } else if (cfgNodeX instanceof CfgNodeEval) {

            CfgNodeEval cfgNode = (CfgNodeEval) cfgNodeX;
            String evalMe = cfgNode.getRight().getVariable().toString();
            String leftString = cfgNode.getLeft().getVariable().toString();
            return (leftString + " = " + "eval(" + evalMe + ")");
        } else if (cfgNodeX instanceof CfgNodeDefine) {

            CfgNodeDefine cfgNode = (CfgNodeDefine) cfgNodeX;
            return ("define(" +
                cfgNode.getSetMe() + ", " +
                cfgNode.getSetTo() + ", " +
                cfgNode.getCaseInsensitive() + ")");
        } else if (cfgNodeX instanceof CfgNodeInclude) {

            CfgNodeInclude cfgNode = (CfgNodeInclude) cfgNodeX;
            String leftString = getPlaceString(cfgNode.getTemp());
            String rightString = getPlaceString(cfgNode.getIncludeMe());
            return (leftString + " = include " + rightString);
        } else if (cfgNodeX instanceof CfgNodeIncludeStart) {
            return ("incStart");
        } else if (cfgNodeX instanceof CfgNodeIncludeEnd) {
            return ("incEnd");
        } else {
            return "to-do: " + cfgNodeX.getClass();
        }
    }

// getPlaceString ******************************************************************

    static String getPlaceString(TacPlace place) {
        if (place.isVariable()) {
            return place.toString();
        } else if (place.isConstant()) {
            return place.toString();
        } else {
            return escapeDot(place.toString(), 20);
        }
    }

// escapeDot ***********************************************************************

    // escapes special characters in the given string, making it suitable for
    // dot output; if the string's length exceeds the given limit, "..." is
    // returned
    static public String escapeDot(String escapeMe, int limit) {
        if (limit > 0 && escapeMe.length() > limit) {
            return "...";
        }
        StringBuilder escaped = new StringBuilder(escapeMe);
        for (int i = 0; i < escaped.length(); i++) {
            char inspectMe = escaped.charAt(i);
            if (inspectMe == '\n' || inspectMe == '\r') {
                // delete these control characters
                escaped.deleteCharAt(i);
                i--;
            } else if (inspectMe == '"' || inspectMe == '\\') {
                // escape this character by prefixing it with a backslash
                escaped.insert(i, '\\');
                i++;
            }
        }
        return escaped.toString();
    }

// dump(ParseTree) *****************************************************************

    // dumps the parse tree
    static void dump(ParseTree parseTree) {
        recursiveDump(parseTree.getRoot(), 0);
    }

// dump(ParseNode) *****************************************************************

    // dumps only the current parse node
    static public void dump(ParseNode parseNode, int level) {
        StringBuilder buf = new StringBuilder(level);
        for (int i = 0; i < level; i++) {
            buf.append(" ");
        }
        String spaces = buf.toString();

        System.out.print(spaces + "Sym: " + parseNode.getSymbol() + ", Name: " +
            parseNode.getName());
        if (parseNode.getLexeme() != null) {
            System.out.print(", Lex: " + parseNode.getLexeme() + ", lineno: " +
                parseNode.getLineno());
        }
        System.out.println();
    }

// recursiveDump *******************************************************************

    // dumps the subtree starting at the current parse node
    static public void recursiveDump(ParseNode parseNode, int level) {
        dump(parseNode, level);
        for (ParseNode child : parseNode.getChildren()) {
            recursiveDump(child, level + 1);
        }
    }

// dump(SymbolTable) ***************************************************************

    static public void dump(SymbolTable symbolTable, String name) {
        System.out.println("***************************************");
        System.out.println("Symbol Table: " + name);
        System.out.println("***************************************");
        System.out.println();
        for (Variable variable : symbolTable.getVariables().values()) {
            dump(variable);
            System.out.println();
        }
    }

// dump(Variable) ******************************************************************

    static public void dump(Variable variable) {
        System.out.println(variable);

        // if it is an array
        if (variable.isArray()) {
            System.out.println("isArray:            true");

            List<Variable> elements = variable.getElements();
            if (!elements.isEmpty()) {
                System.out.print("elements:           ");
                for (Variable element : elements) {
                    System.out.print(element.getName() + " ");
                }
                System.out.println();
            }
        }

        // if it is an array element
        if (variable.isArrayElement()) {
            System.out.println("isArrayElement:     true");
            System.out.println("enclosingArray:     " +
                variable.getEnclosingArray().getName());
            System.out.println("topEnclosingArray:  " +
                variable.getTopEnclosingArray().getName());
            TacPlace indexPlace = variable.getIndex();
            System.out.print("index type:         ");
            if (indexPlace.isLiteral()) {
                System.out.println("literal");
            } else if (indexPlace.isVariable()) {
                System.out.println("variable");
            } else if (indexPlace.isConstant()) {
                System.out.println("constant");
            } else {
                System.out.println("UNKNOWN!");
            }
            System.out.print("indices:            ");
            for (TacPlace index : variable.getIndices()) {
                System.out.print(index + " ");
            }
            System.out.println();
        }

        // if it is a variable variable
        TacPlace depPlace = variable.getDependsOn();
        if (depPlace != null) {
            System.out.println("dependsOn:          " + depPlace.toString());
        }

        // print array elements indexed by this variable
        List<Variable> indexFor = variable.getIndexFor();
        if (!indexFor.isEmpty()) {
            System.out.print("indexFor:           ");
            for (Variable indexed : indexFor) {
                System.out.print(indexed + " ");
            }
            System.out.println();
        }
    }

// dump(ConstantsTable) ************************************************************

    static public void dump(ConstantsTable constantsTable) {
        System.out.println("***************************************");
        System.out.println("Constants Table ");
        System.out.println("***************************************");
        System.out.println();
        for (Constant constant : constantsTable.getConstants().values()) {
            System.out.println(constant.getLabel());
        }
        System.out.println();
        System.out.println("Insensitive Groups:");
        System.out.println();
        for (List<Constant> insensitiveGroup : constantsTable.getInsensitiveGroups().values()) {
            System.out.print("* ");
            for (Constant anInsensitiveGroup : insensitiveGroup) {
                System.out.print(anInsensitiveGroup.getLabel() + " ");
            }
            System.out.println();
        }
        System.out.println();
    }

    static public void dump(IncDomAnalysis analysis) {
        for (Map.Entry<CfgNode, AnalysisNode> entry : analysis.getAnalysisInfo().getMap().entrySet()) {
            CfgNode cfgNode = entry.getKey();
            IntraAnalysisNode analysisNode = (IntraAnalysisNode) entry.getValue();
            System.out.println("dominators for cfg node " + cfgNode.toString() + ", " + cfgNode.getOrigLineno());
            Dumper.dump(analysisNode.getInValue());
        }
    }

    static public void dump(InterAnalysis analysis, String path, String filename) {

        // create directory
        (new File(path)).mkdir();

        try {
            Writer writer = new FileWriter(path + '/' + filename);

            // nothing to do for dummy analysis
            if (analysis instanceof DummyLiteralAnalysis || analysis instanceof DummyAliasAnalysis) {
                writer.write("Dummy Analysis" + linesep);
                writer.close();
                return;
            }

            List<TacFunction> functions = analysis.getFunctions();
            InterAnalysisInfo analysisInfoNew = analysis.getInterAnalysisInfo();

            if (analysis instanceof LiteralAnalysis) {
                writer.write(linesep + "Default Lattice Element:" + linesep + linesep);
                dump(LiteralLatticeElement.DEFAULT, writer);
            }

            // for each function...
            for (TacFunction function : functions) {
                Cfg cfg = function.getCfg();
                writer.write(linesep + "****************************************************" + linesep);
                writer.write(function.getName() + linesep);
                writer.write("****************************************************" + linesep + linesep);
                // for each Cfg node...
                for (Iterator<CfgNode> bft = cfg.bfIterator(); bft.hasNext(); ) {
                    CfgNode cfgNode = bft.next();
                    writer.write("----------------------------------------" + linesep);
                    writer.write(cfgNode.getFileName() + ", " + cfgNode.getOrigLineno() +
                        ", " + makeCfgNodeName(cfgNode) + linesep);
                    dump(analysisInfoNew.getAnalysisNode(cfgNode).getRecycledFoldedValue(), writer);
                }
                writer.write("----------------------------------------" + linesep);
            }

            writer.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

// dump(AnalysisNode) **************************************************************

    static public void dump(InterAnalysisNode node) {
        System.out.print("Transfer Function: ");
        try {
            System.out.println(node.getTransferFunction().getClass().getName());
        } catch (NullPointerException e) {
            System.out.println("<<null>>");
        }
        // dump the lattice element for each context
        for (LatticeElement element : node.getPhi().values()) {
            System.out.println("~~~~~~~~~~~~~~~");
            dump(element);
        }
    }

    static public void dump(LatticeElement elementX) {

        try {
            Writer writer = new OutputStreamWriter(System.out);
            dump(elementX, writer);
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("SNH:" + e.getStackTrace());
        }
    }

//  dump(LatticeElement) ************************************************************

    static public void dump(LatticeElement elementX, Writer writer) throws IOException {
        if (elementX instanceof AliasLatticeElement) {
            AliasLatticeElement element = (AliasLatticeElement) elementX;
            dump(element.getMustAliases(), writer);
            dump(element.getMayAliases(), writer);
        } else if (elementX instanceof LiteralLatticeElement) {
            LiteralLatticeElement element = (LiteralLatticeElement) elementX;

            // dump non-default literal mappings
            for (Map.Entry<TacPlace, Literal> entry : element.getPlaceToLit().entrySet()) {
                TacPlace place = entry.getKey();
                Literal lit = entry.getValue();
                writer.write(place + ":      " + lit + linesep);
            }
        } else if (elementX instanceof DepLatticeElement) {
            dumpComplete((DepLatticeElement) elementX, writer);
        } else if (elementX instanceof IncDomLatticeElement) {
            IncDomLatticeElement element = (IncDomLatticeElement) elementX;
            List<CfgNode> dominators = element.getDominators();
            if (dominators.isEmpty()) {
                System.out.println("<<empty>>");
            } else {
                for (CfgNode dominator : dominators) {
                    System.out.println(dominator.toString() + ", " + dominator.getOrigLineno());
                }
            }
        } else if (elementX instanceof LatticeElementBottom) {
            writer.write(linesep + "Bottom Element" + linesep + linesep);
        } else if (elementX == null) {
            writer.write(linesep + "<<null>>" + linesep + linesep);
        } else {
            throw new RuntimeException("SNH: " + elementX.getClass());
        }

        writer.flush();
    }

    // returns true if this variable should not be dumped, because it is a
    // - temporary
    // - shadow
    // - variable of a builtin function
    private static boolean doNotDump(Variable var) {
        // EFF: "endsWith" technique not too elegant; might also lead
        // to "rootkit effects"...; alternative would be: save additional
        // field for variables
        return var.isTemp() ||
            var.getName().endsWith(InternalStrings.gShadowSuffix) ||
            var.getName().endsWith(InternalStrings.gShadowSuffix) ||
            BuiltinFunctions.isBuiltinFunction(var.getSymbolTable().getName());
    }

//  ********************************************************************************

    static public void dumpComplete(DepLatticeElement element, Writer writer)
        throws IOException {

        // dump non-default dep mappings
        writer.write(linesep + "DEP MAPPINGS" + linesep + linesep);
        for (Map.Entry<TacPlace, DepSet> entry : element.getPlaceToDep().entrySet()) {
            TacPlace place = entry.getKey();
            DepSet depSet = entry.getValue();
            writer.write(place + ":      " + depSet + linesep);
        }

        // dump non-default array labels
        writer.write(linesep + "ARRAY LABELS" + linesep + linesep);
        for (Map.Entry<Variable, DepSet> entry : element.getArrayLabels().entrySet()) {
            Variable var = entry.getKey();
            DepSet arrayLabel = entry.getValue();
            writer.write(var + ":      " + arrayLabel + linesep);
        }
    }

    static public void dump(DepLatticeElement element) {
        try {
            Writer writer = new OutputStreamWriter(System.out);
            dump(element, writer);
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("SNH:" + e.getStackTrace());
        }
    }

    // like dumpComplete, but only prints
    // - non-temporaries
    // - non-shadows
    // - variables of non-builtin functions
    static public void dump(DepLatticeElement element, Writer writer) throws IOException {
        // dump non-default taint mappings
        writer.write(linesep + "TAINT MAPPINGS" + linesep + linesep);
        for (Map.Entry<TacPlace, DepSet> entry : element.getPlaceToDep().entrySet()) {
            TacPlace place = entry.getKey();
            if (place.isVariable()) {
                Variable var = place.getVariable();
                if (doNotDump(var)) {
                    continue;
                }
            }
            writer.write(place + ":      " + entry.getValue() + linesep);
        }

        // dump non-default array labels
        writer.write(linesep + "ARRAY LABELS" + linesep + linesep);
        for (Map.Entry<Variable, DepSet> entry : element.getArrayLabels().entrySet()) {
            Variable variable = entry.getKey();
            if (doNotDump(variable)) {
                continue;
            }
            writer.write(variable + ":      " + entry.getValue() + linesep);
        }
    }

    static public void dump(MustAliases mustAliases, Writer writer) throws IOException {
        writer.write("u{ ");
        for (MustAliasGroup group : mustAliases.getGroups()) {
            dump(group, writer);
            writer.write(" ");
        }
        writer.write("}" + linesep);
    }

    static public void dump(MustAliasGroup mustAliasGroup, Writer writer) throws IOException {
        writer.write("( ");
        for (Variable variable : mustAliasGroup.getVariables()) {
            writer.write(variable + " ");
        }
        writer.write(")");
    }

    static public void dump(MayAliases mayAliases, Writer writer) throws IOException {
        writer.write("a{ ");
        for (MayAliasPair pair : mayAliases.getPairs()) {
            dump(pair, writer);
            writer.write(" ");
        }
        writer.write("}" + linesep);
    }

    static public void dump(MayAliasPair mayAliasPair, Writer writer) throws IOException {
        Set<Variable> pair = mayAliasPair.getPair();
        Object[] pairArray = pair.toArray();
        writer.write("(" + pairArray[0] + " " + pairArray[1] + ")" + linesep);
    }

    static public void dumpFunction2ECS(Map<TacFunction, ECS> function2ECS) {
        for (Map.Entry<TacFunction, ECS> entry : function2ECS.entrySet()) {
            TacFunction function = entry.getKey();
            ECS ecs = entry.getValue();
            System.out.println("ECS for Function " + function.getName() + ": ");
            System.out.println(ecs);
            System.out.println();
        }
    }
}