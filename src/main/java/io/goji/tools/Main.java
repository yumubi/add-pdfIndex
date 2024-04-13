package io.goji.tools;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfOutline;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.navigation.PdfExplicitDestination;
import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.layout.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

import java.util.*;

import static java.lang.StringTemplate.STR;

public class Main {

    private static final String CONFIG_FILE = "config.properties";
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) {

        try {

            Properties appProps = new Properties();
            appProps.load(Main.class.getClassLoader().getResourceAsStream(CONFIG_FILE));
            String indentSpaces = appProps.getProperty("indentSpaces"); // 相邻父子层级之间的空格数,建议为1
            String indexFile = appProps.getProperty("indexFile");
            String originalPDF = appProps.getProperty("originalPDF");
            String outputPDF = appProps.getProperty("outputPDF");

            LOGGER.debug(STR."indentSpaces: \{indentSpaces}, indexFile: \{indexFile}, originalPDF: \{originalPDF}, outputPDF: \{outputPDF}");





            //todo 支持properties写相对路径

            System.out.println(STR."reading index file: \{indexFile}");
            try (FileReader fileReader = new FileReader(indexFile)) {
                BufferedReader bufferedReader = new BufferedReader(fileReader);
                List<String> indexTextLines = bufferedReader.lines().toList();
                if (indexTextLines.isEmpty()) {
                    throw new RuntimeException("invalid index");
                }
                Integer pageStart = Integer.parseInt(
                        indexTextLines.get(0)
                                .replaceAll("pageStart", "")
                                .trim()
                );
                int difference = pageStart - 1;


                List<Bookmark> parseBookmarks = indexTextLines.stream().skip(1)
                        .map(line -> {
                            int spaceNum = line.indexOf("-");
                            if(spaceNum < 0) {
                                return null;
                            }
                            String simpleLine = line.trim().replace("- ", "");
                            int lastSpaceIndex = simpleLine.lastIndexOf(" ");
                            String pageNumberString = simpleLine.substring(lastSpaceIndex + 1);
                            Integer pageNo = pageNumberString.startsWith("[") ?
                                    Integer.parseInt(pageNumberString.replace("[", "").replace("]", "")) :
                                    (Integer.parseInt(pageNumberString) + difference);
                            Bookmark bookmark = new Bookmark();
                            bookmark.name = simpleLine.substring(0, lastSpaceIndex);
                            bookmark.pdfPageNumber = pageNo;
                            bookmark.depth = spaceNum / Integer.parseInt(indentSpaces);

                            return bookmark;
                        })
                        .filter(Objects::nonNull)
                        .toList();

                List<Bookmark> bookmarkTree = Bookmark.toTree(parseBookmarks);
                bookmarkTree.forEach(bookmark -> LOGGER.debug(STR."\{bookmark.toString()},\n"));




                PdfReader pdfReader = new PdfReader(Objects.requireNonNull(Main.class.getClassLoader().getResourceAsStream(originalPDF)));

                PdfDocument pdfDoc = new PdfDocument(pdfReader, new PdfWriter(outputPDF));
                PdfMerger merger = new PdfMerger(pdfDoc);

                PdfDocument tempDoc = new PdfDocument(new PdfReader(originalPDF));
                int numberOfPages = tempDoc.getNumberOfPages();
                for (int i = 1; i <= numberOfPages; i++) {
                    merger.merge(tempDoc, i, i);
                }
                tempDoc.close();

                // Add bookmarks
                PdfOutline rootOutline = pdfDoc.getOutlines(false);
                writeBookmark(pdfDoc, bookmarkTree, rootOutline);

                pdfDoc.close();



            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    private static void writeBookmark(PdfDocument pdfDoc, List<Bookmark> bookmarkTree, PdfOutline outlineNode) {
        for (Bookmark bookmark : bookmarkTree) {
            PdfOutline subNode = outlineNode.addOutline(bookmark.name);
            subNode.addDestination(PdfExplicitDestination.createFit(pdfDoc.getPage(bookmark.pdfPageNumber)));
            if (bookmark.children != null) {
                writeBookmark(pdfDoc, bookmark.children, subNode);
            }
        }
    }
}


class Bookmark {

    private static final Logger LOGGER = LoggerFactory.getLogger(Bookmark.class);
    String name;
    Integer pdfPageNumber;
    Integer depth;

    List<Bookmark> children;

    public static List<Bookmark> toTree(List<Bookmark> bookmarks) {
        Bookmark root = new Bookmark();
        root.name = "root";
        root.depth = -1;
        root.children = new ArrayList<>();
        addNode(root, null, bookmarks, 0);
        return root.children;
    }

    /**
     * Add a node to the tree
     * @param curRoot current root node
     * @param namePrefix prefix of the node name
     * @param bookmarks list of bookmarks
     * @param index current index in the list of bookmarks
     * @return the index of the next node in the list of bookmarks
     */
    public static Integer addNode(Bookmark curRoot, String namePrefix, List<Bookmark> bookmarks, Integer index) {
        Integer prefixCount = 0; //大章节depth为0不加前缀, 小章节depth >= 1, 加前缀, 例如"1.1 第1章第1节"
        while (index < bookmarks.size()) {
            Bookmark node = bookmarks.get(index);
            String prefix = namePrefix == null ? prefixCount.toString() : STR."\{namePrefix}.\{prefixCount}";

            if(curRoot.depth + 1 == node.depth) { //node为直接子节点
                if(curRoot.children == null) {
                    curRoot.children = new ArrayList<>();
                }
                curRoot.children.add(node);
                if(node.depth > 0) { // add prefix to node name when depth >= 1
                    prefixCount++;
                    prefix = namePrefix == null ? prefixCount.toString() : STR."\{namePrefix}.\{prefixCount}";
                    node.name = STR."\{prefix} \{node.name}";
                }
                if(node.depth == 0 && bookmarks.get(Math.min(index + 1, bookmarks.size() - 1)).depth == 1) { // next node is a direct child of root
                    prefixCount++; // 更新大章节前缀, 从 "第一章" 到 "第二章" 前缀 1 -> 2

                    LOGGER.debug("next node is a direct child of root, %s, content is %s, prefixCount is %d".formatted(index, node.toString(), prefixCount));
                }
                index++;
            } else if(curRoot.depth + 1 < node.depth) { // dive into next child root
                Bookmark nextRoot = curRoot.children.getLast();
                index = addNode(nextRoot, prefix, bookmarks, index);
            } else if(curRoot.depth >= node.depth) {
                return index;
            }
        }
        return index;
    }

    @Override
    public String toString() {
        return STR."""
    {
        "name": "\{name},",
        "pdfPageNumber": \{pdfPageNumber},
        "depth": \{depth},
        "children": \{Optional.ofNullable(children).isPresent() ? STR."""
        [
            \{children.stream().map(Bookmark::toString).reduce((a, b) -> STR."\{a},\t\{b}").get()}
        ]"""
        : "null"
    }
    }""";


    }
}
