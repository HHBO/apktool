/**
 *  Copyright 2014 Ryszard Wiśniewski <brut.alll@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package brut.androlib.mod;

import java.io.*;
import org.antlr.runtime.*;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import org.apache.commons.io.IOUtils;
import org.jf.dexlib2.writer.builder.DexBuilder;
import org.jf.smali.*;

/**
 * @author Ryszard Wiśniewski <brut.alll@gmail.com>
 */
public class SmaliMod {

    public static boolean assembleSmaliFile(String smali, DexBuilder dexBuilder, boolean verboseErrors,
                                            boolean printTokens, File smaliFile) throws IOException, RuntimeException, RecognitionException {

        InputStream is = new ByteArrayInputStream(smali.getBytes());
        return assembleSmaliFile(is, dexBuilder, verboseErrors, printTokens, smaliFile);
    }

    public static boolean assembleSmaliFile(InputStream is,DexBuilder dexBuilder, boolean verboseErrors,
                                            boolean printTokens, File smaliFile) throws IOException, RecognitionException {

        // copy our filestream into a tmp file, so we don't overwrite
        File tmp = File.createTempFile("BRUT",".bak");
        tmp.deleteOnExit();

        OutputStream os = new FileOutputStream(tmp);
        IOUtils.copy(is, os);
        os.close();

        return assembleSmaliFile(tmp,dexBuilder, verboseErrors, printTokens);
    }

    public static boolean assembleSmaliFile(File smaliFile,DexBuilder dexBuilder, boolean verboseErrors,
                                            boolean printTokens) throws IOException, RecognitionException {
        //主要的smali2dex代码所在

        CommonTokenStream tokens;
        LexerErrorInterface lexer;

        InputStream is = new FileInputStream(smaliFile);
        InputStreamReader reader = new InputStreamReader(is, "UTF-8");

        lexer = new smaliFlexLexer(reader);
        ((smaliFlexLexer)lexer).setSourceFile(smaliFile);//初始化 原始smali文件夹
        tokens = new CommonTokenStream((TokenSource) lexer);

        if (printTokens) {
            tokens.getTokens();

            for (int i=0; i<tokens.size(); i++) {
                Token token = tokens.get(i);
                if (token.getChannel() == smaliParser.HIDDEN) { //调用第三方smaliPaarser
                    continue;
                }

                System.out.println(smaliParser.tokenNames[token.getType()] + ": " + token.getText());
            }
        }

        smaliParser parser = new smaliParser(tokens);
        parser.setVerboseErrors(verboseErrors);

        smaliParser.smali_file_return result = parser.smali_file();

        if (parser.getNumberOfSyntaxErrors() > 0 || lexer.getNumberOfSyntaxErrors() > 0) {
            return false;
        }

        CommonTree t = (CommonTree) result.getTree();

        CommonTreeNodeStream treeStream = new CommonTreeNodeStream(t);
        treeStream.setTokenStream(tokens);

        smaliTreeWalker dexGen = new smaliTreeWalker(treeStream);

        dexGen.setVerboseErrors(verboseErrors);
        dexGen.setDexBuilder(dexBuilder);
        dexGen.smali_file();

        is.close();
        reader.close();

        return dexGen.getNumberOfSyntaxErrors() == 0;
    }
}
