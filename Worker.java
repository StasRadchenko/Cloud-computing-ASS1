package com.company;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import com.lowagie.text.pdf.codec.Base64.InputStream;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class Worker {
    public static void main(String [] args){
    	URL url;
    	Tesseract tesseract = new Tesseract();
    	java.io.InputStream in;
		try {
			url = new URL("http://files.microscan.com/Technology/OCR/ocr_font_examples.jpg");
			in = url.openStream();
			Files.copy(in, Paths.get("fuckYou.jpg"), StandardCopyOption.REPLACE_EXISTING);
	    	in.close();
	    	String text = tesseract.doOCR(new File ("fuckYou.jpg"));
	    	System.out.println(text);
	    	
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TesseractException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	
        
        
        
    }
}
