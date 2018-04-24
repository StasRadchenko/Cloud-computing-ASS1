package com.company;



import java.io.File;

import com.asprise.ocr.Ocr;



public class Worker {
    public static void main(String [] args){
    	Ocr.setUp();
		Ocr ocr = new Ocr();
		ocr.startEngine("eng", Ocr.SPEED_SLOW);
		//URL url = new URL("http://www.idautomation.com/ocr-a-and-ocr-b-fonts/new_sizes_ocr.png");
		//BufferedImage image = ImageIO.read(url);
		String test = ocr.recognize(new File [] {new File("3d-text.jpg")}, Ocr.RECOGNIZE_TYPE_ALL, Ocr.OUTPUT_FORMAT_PLAINTEXT);
		System.out.println(test);
		ocr.stopEngine();
    	
    	
    	
    	
    	
        
        
        
    }
}
