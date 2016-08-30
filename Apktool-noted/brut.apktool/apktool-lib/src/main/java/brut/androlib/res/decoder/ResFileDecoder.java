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

package brut.androlib.res.decoder;

import brut.androlib.AndrolibException;
import brut.androlib.err.CantFind9PatchChunk;
import brut.androlib.res.data.ResResource;
import brut.androlib.res.data.value.ResBoolValue;
import brut.androlib.res.data.value.ResFileValue;
import brut.directory.DirUtil;
import brut.directory.Directory;
import brut.directory.DirectoryException;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Ryszard Wiśniewski <brut.alll@gmail.com>
 */
public class ResFileDecoder {
    private final ResStreamDecoderContainer mDecoders;

    public ResFileDecoder(ResStreamDecoderContainer decoders) {
        this.mDecoders = decoders;
    }

    public void decode(ResResource res, Directory inDir, Directory outDir)
            throws AndrolibException {

        ResFileValue fileValue = (ResFileValue) res.getValue();
        String inFileName = fileValue.getStrippedPath();
        String outResName = res.getFilePath();
        String typeName = res.getResSpec().getType().getName();//获取文件的名字

        String ext = null;
        String outFileName;
        int extPos = inFileName.lastIndexOf(".");
        if (extPos == -1) {
            outFileName = outResName;
        } else {
            ext = inFileName.substring(extPos).toLowerCase();
            outFileName = outResName + ext;
        }

        try {
            if (typeName.equals("raw")) {
                decode(inDir, inFileName, outDir, outFileName, "raw");//如果是raw文件，则需调用解码函数
                return;
            }
            if (typeName.equals("drawable") || typeName.equals("mipmap")) {
                if (inFileName.toLowerCase().endsWith(".9" + ext)) {
                    outFileName = outResName + ".9" + ext;

                    // check for htc .r.9.png
                    if (inFileName.toLowerCase().endsWith(".r.9" + ext)) {
                        outFileName = outResName + ".r.9" + ext;
                    }

                    // check for samsung qmg & spi
                    if (inFileName.toLowerCase().endsWith(".qmg") || inFileName.toLowerCase().endsWith(".spi")) {
                        copyRaw(inDir, outDir, outFileName);//原始文件直接复制
                        return;
                    }

                    // check for xml 9 patches which are just xml files
                    if (inFileName.toLowerCase().endsWith(".xml")) {
                        decode(inDir, inFileName, outDir, outFileName, "xml");//解码xml文件
                        return;
                    }

                    try {
                        decode(inDir, inFileName, outDir, outFileName, "9patch");//解码9patch文件
                        return;
                    } catch (CantFind9PatchChunk ex) {
                        LOGGER.log(
                                Level.WARNING,
                                String.format(
                                        "Cant find 9patch chunk in file: \"%s\". Renaming it to *.png.",
                                        inFileName), ex);
                        outDir.removeFile(outFileName);
                        outFileName = outResName + ext;
                    }
                }
                if (!".xml".equals(ext)) {
                    decode(inDir, inFileName, outDir, outFileName, "raw");
                    return;
                }
            }

            decode(inDir, inFileName, outDir, outFileName, "xml");
        } catch (AndrolibException ex) {
            LOGGER.log(Level.SEVERE, String.format(
                    "Could not decode file, replacing by FALSE value: %s",
                    inFileName), ex);
            res.replace(new ResBoolValue(false, 0, null));
        }
    }

    public void decode(Directory inDir, String inFileName, Directory outDir,
                       String outFileName, String decoder) throws AndrolibException {
        try (
                InputStream in = inDir.getFileInput(inFileName);
                OutputStream out = outDir.getFileOutput(outFileName)
        ) {
            mDecoders.decode(in, out, decoder); //关键的raw/xml文件解码，主要由decoder参数来控制
            //这里要跳的话，注意不要混乱，xml和raw的解码函数并不一样
        } catch (DirectoryException | IOException ex) {
            throw new AndrolibException(ex);
        }
    }

    public void copyRaw(Directory inDir, Directory outDir, String filename) throws AndrolibException {
        try {
            DirUtil.copyToDir(inDir, outDir, filename);  //复制原文件
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    public void decodeManifest(Directory inDir, String inFileName,
                               Directory outDir, String outFileName) throws AndrolibException {
        try (
                InputStream in = inDir.getFileInput(inFileName);
                OutputStream out = outDir.getFileOutput(outFileName)
        ) {
            ((XmlPullStreamDecoder) mDecoders.getDecoder("xml")).decodeManifest(in, out);//xml解码关键点
        } catch (DirectoryException | IOException ex) {
            throw new AndrolibException(ex);
        }
    }

    private final static Logger LOGGER = Logger.getLogger(ResFileDecoder.class.getName());
}