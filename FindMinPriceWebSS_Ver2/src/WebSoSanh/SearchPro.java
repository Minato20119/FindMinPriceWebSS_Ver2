/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package WebSoSanh;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;

/**
 *
 * @author Minato
 */
public class SearchPro {

    private static final String REGEX_GET_CODE_OF_PRODUCT = "[0-9a-z]+\\s[0-9a-z]+$";
    private static final String REGEX_GET_BLOCK_CONTAINS_PRICE = "<h3 class=\"title[\\s|\\S]*?<\\/div>";
    private static final String REGEX_GET_TITLE_OF_PRODUCT = "(target=\"_blank\">)(.+)(<\\/a><\\/h3>)";
    private static final String REGEX_GET_PRICE_IN_BLOCK = "\\s([0-9.+]+)(\\sđ)\\s";
    private static final String REGEX_GET_NUMBER_PAGES = "(data-page-index=\")([0-9]+)";

    // set giá nhỏ nhất để bỏ qua nó (chỉ lấy giá từ 500k trở lên)
    private static final int DEFAULT_PRICE_MIN = 500000;

    public StringBuilder searchProduct(String linkPathFile) {
        StringBuilder builder = new StringBuilder();

        FileInputStream fileInputStream = null;

        try {
            fileInputStream = new FileInputStream(linkPathFile);
            Reader reader = null;

            try {
                reader = new InputStreamReader(fileInputStream, "utf8");
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(SearchPro.class.getName()).log(Level.SEVERE, null, ex);
            }

            BufferedReader inputText = new BufferedReader(reader);

            String tempProduct;
            int count = 1;

            try {
                while ((tempProduct = inputText.readLine()) != null) {

                    String nameProduct;

                    // remove 1234, bếp từ :v
                    if (tempProduct.contains(",")) {
                        nameProduct = tempProduct.substring(tempProduct.indexOf(",") + 1, tempProduct.length());
                    } else {
                        nameProduct = tempProduct;
                    }

                    // Nếu line = null => continue
                    if (nameProduct.length() == 0) {
                        continue;
                    }

                    System.out.println(count++ + ": " + nameProduct);
                    builder.append(count - 1).append(": ").append(nameProduct).append("\n");

                    String product = nameProduct.replaceAll("[-–]", " ").toLowerCase();
                    product = product.replaceAll("\\s\\s+", " ");

                    // Check tên sản phẩm chỉ với 2 mã code ở cuối
                    String codeOfProduct = "";

                    Pattern patternCode = Pattern.compile(REGEX_GET_CODE_OF_PRODUCT);
                    Matcher matcherCode = patternCode.matcher(product);

                    while (matcherCode.find()) {
                        codeOfProduct = matcherCode.group(0);
                    }

                    // Get source code of websosanh
                    int defaultPrice = 100000000, price, sumPages = 1, numberPage = 1;
                    do {

                        String encodeSingleText = URLEncoder.encode(nameProduct.trim(), "UTF-8");

                        encodeSingleText = "https://websosanh.vn/s/" + encodeSingleText;

                        String urlText = encodeSingleText + "?pi=" + (String.valueOf(numberPage)) + ".htm";

                        URL url = new URL(urlText);
                        URLConnection connectURL = url.openConnection();
                        System.out.println("Done connect...");

                        long timeStart = System.currentTimeMillis();

                        // Get inputStreamReader
                        try (BufferedReader inputURL = new BufferedReader(new InputStreamReader(
                                connectURL.getInputStream(), StandardCharsets.UTF_8))) {

                            String tempText;
                            String textLineStream = "";
                            String containsTextOfPage = "";

                            while ((tempText = inputURL.readLine()) != null) {
                                textLineStream += tempText;

                                // Get text chứa số trang của một sản phẩm
                                if (textLineStream.contains("data-page-index")) {
                                    containsTextOfPage += tempText;
                                }

                                // Dừng lấy inputStream
                                if (textLineStream.contains("Từ khóa tương tự") || textLineStream.contains("shorter-serach-suggestion__link")) {
                                    break;
                                }
                            }

                            long timeEnd = System.currentTimeMillis();
                            System.out.println("Duration: " + (timeEnd - timeStart));

                            // Get text chứa giá
                            Pattern pattern = Pattern.compile(REGEX_GET_BLOCK_CONTAINS_PRICE);
                            Matcher matcher = pattern.matcher(textLineStream.toLowerCase());

                            while (matcher.find()) {

                                String textContainsPrice = matcher.group(0);

                                // Hết Hàng
                                if (textContainsPrice.compareTo("out-of-stock") == 0) {
                                    continue;
                                }

                                // Get title product
                                Pattern pattern1 = Pattern.compile(REGEX_GET_TITLE_OF_PRODUCT);
                                Matcher matcher1 = pattern1.matcher(textContainsPrice);

                                // So sánh tên sản phẩm từ file với tên sản phẩm tên websosanh
                                String titleOfProduct = "";

                                while (matcher1.find()) {
                                    titleOfProduct = matcher1.group(2).replaceAll("[-–]", " ").toLowerCase();
                                    titleOfProduct = titleOfProduct.replaceAll("\\s\\s+", " ");
                                }

                                // Nếu khác với tên sản phẩm trong file thì sẽ bỏ qua
                                if (!titleOfProduct.contains(codeOfProduct)) {
                                    continue;
                                }

                                textContainsPrice = textContainsPrice.replaceAll("giá từ", "");

                                // Tìm giá
                                Pattern pattern2 = Pattern.compile(REGEX_GET_PRICE_IN_BLOCK);
                                Matcher matcher2 = pattern2.matcher(textContainsPrice);

                                while (matcher2.find()) {
                                    // Continute if price set = 1
                                    if (matcher2.group(1).compareTo("1") == 0) {
                                        continue;
                                    }
                                    // Get price
                                    price = Integer.parseInt(matcher2.group(1).replace(".", ""));

                                    if (price <= DEFAULT_PRICE_MIN) {
                                        continue;
                                    }

                                    if (price < defaultPrice) {
                                        defaultPrice = price;
                                    }
                                }
                            }

                            // Page đầu tiên
                            if (sumPages == 1) {

                                // Chứa data-page-index => page > 1
                                if (!containsTextOfPage.equals("")) {

                                    // Get số trang của sản phẩm
                                    Pattern pattern3 = Pattern.compile(REGEX_GET_NUMBER_PAGES);
                                    Matcher matcher3 = pattern3.matcher(containsTextOfPage);

                                    while (matcher3.find()) {

                                        int tempNumberPages = Integer.parseInt(matcher3.group(2));

                                        if (sumPages < tempNumberPages) {
                                            sumPages = tempNumberPages;
                                        }
                                    }
                                }
                            }
                        }

                        numberPage++;

                    } while (numberPage <= sumPages);

                    System.out.println("-----------------------------------------> PriceMin: " + defaultPrice + " đ");
                    builder.append("----------------------------------------> PriceMin: ").append(defaultPrice).append(" đ").append("\n");
                }
            } catch (IOException ex) {
                Logger.getLogger(SearchPro.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(SearchPro.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fileInputStream.close();
            } catch (IOException ex) {
                Logger.getLogger(SearchPro.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return builder;
    }

    public void output(String linkFile, String contain) throws IOException {
        try (Writer out = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(linkFile), "UTF-8"))) {
            out.write(contain);
            JOptionPane.showMessageDialog(null, "Done");
        }
    }
}
