package in.rkvsraman.indicocr.webservice;

import java.util.concurrent.Callable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.xml.bind.JAXBContext;


import org.apache.commons.exec.CommandLine;
import org.odftoolkit.odfdom.dom.element.draw.DrawTextBoxElement;
import org.odftoolkit.odfdom.dom.element.style.StyleMasterPageElement;
import org.odftoolkit.odfdom.incubator.doc.style.OdfStylePageLayout;
import org.odftoolkit.odfdom.type.Color;
import org.odftoolkit.simple.TextDocument;
import org.odftoolkit.simple.draw.FrameRectangle;
import org.odftoolkit.simple.draw.FrameStyleHandler;
import org.odftoolkit.simple.draw.Image;
import org.odftoolkit.simple.draw.Textbox;
import org.odftoolkit.simple.style.Border;
import org.odftoolkit.simple.style.Font;
import org.odftoolkit.simple.style.PageLayoutProperties;
import org.odftoolkit.simple.style.StyleTypeDefinitions.AnchorType;
import org.odftoolkit.simple.style.StyleTypeDefinitions.CellBordersType;
import org.odftoolkit.simple.style.StyleTypeDefinitions.FontStyle;
import org.odftoolkit.simple.style.StyleTypeDefinitions.FrameHorizontalPosition;
import org.odftoolkit.simple.style.StyleTypeDefinitions.FrameVerticalPosition;
import org.odftoolkit.simple.style.StyleTypeDefinitions.HorizontalRelative;
import org.odftoolkit.simple.style.StyleTypeDefinitions.PrintOrientation;
import org.odftoolkit.simple.style.StyleTypeDefinitions.SupportedLinearMeasure;
import org.odftoolkit.simple.style.StyleTypeDefinitions.VerticalRelative;
import org.odftoolkit.simple.text.Paragraph;

import com.sun.tools.internal.xjc.runtime.JAXBContextFactory;

import in.rkvsraman.ocr.olena.scribocli.ObjectFactory;
import in.rkvsraman.ocr.olena.scribocli.PcGts;
import in.rkvsraman.ocr.olena.scribocli.PcGts.Page;
import in.rkvsraman.ocr.olena.scribocli.PcGts.Page.HorizontalSeparatorRegion.Coords.Point;
import in.rkvsraman.ocr.olena.scribocli.PcGts.Page.ImageRegion;
import in.rkvsraman.ocr.olena.scribocli.PcGts.Page.TextRegion;
import in.rkvsraman.ocr.olena.scribocli.PcGts.Page.TextRegion.Line;
import in.rkvsraman.ocr.olena.scribocli.PcGts.Page.TextRegion.Line.Coords;
import io.vertx.ext.web.RoutingContext;

public class ODTConverter implements Callable<String> {

	static double point_to_centimeter = 0.035277778;
	int reduce_y = 0;
	double x_factor, y_factor;
	double a4_width_in_cm = 21, a4_height_in_cm = 29.7;
	private RoutingContext context;
	private String filePath;
	private File outputFile;
	private CommandLine command;
	private String language;

	public ODTConverter(RoutingContext context, String filePath, File outputFile, CommandLine command,
			String language) {
		this.context = context;
		this.filePath = filePath;
		this.outputFile = outputFile;
		
		this.command = command;
		this.language = language;
	}

	@Override
	public String call() throws Exception {
		// TODO Auto-generated method stub
		JAXBContext jc = JAXBContext.newInstance(ObjectFactory.class);
		File xml = outputFile;

		BufferedImage bi = ImageIO.read(new File(filePath));

		PcGts pcgts = (PcGts) jc.createUnmarshaller().unmarshal(xml);
		Page page = pcgts.getPage();

		calculateReduceY(page);
		TextDocument outputOdt = TextDocument.newTextDocument();

		StyleMasterPageElement defaultPage = outputOdt.getOfficeMasterStyles().getMasterPage("Standard");
		String pageLayoutName = defaultPage.getStylePageLayoutNameAttribute();
		System.out.println("Page layout name:" + pageLayoutName);
		OdfStylePageLayout pageLayoutStyle = defaultPage.getAutomaticStyles().getPageLayout(pageLayoutName);
		PageLayoutProperties pageLayoutProperties = PageLayoutProperties
				.getOrCreatePageLayoutProperties(pageLayoutStyle);
		pageLayoutProperties.setPageHeight(a4_height_in_cm * 10);
		pageLayoutProperties.setPageWidth(a4_width_in_cm * 10);
		pageLayoutProperties.setPrintOrientation(PrintOrientation.PORTRAIT);
		pageLayoutProperties.setBorders(CellBordersType.ALL_FOUR, Border.NONE);
		pageLayoutProperties.setMarginLeft(0);
		pageLayoutProperties.setMarginRight(0);
		pageLayoutProperties.setMarginTop(0);
		pageLayoutProperties.setMarginBottom(0);

		x_factor = page.getImageWidth() / a4_width_in_cm;
		y_factor = page.getImageHeight() / a4_height_in_cm;

		System.out.println("Factors:" + x_factor + " " + y_factor);

		Paragraph para1 = outputOdt.addParagraph("");

		for (ImageRegion ir : page.getImageRegion()) {

			int yMin = ir.getYMin() - reduce_y;
			int yMax = ir.getYMax() - reduce_y;
			FrameRectangle fr = new FrameRectangle(ir.getXMin() / x_factor, yMin / y_factor,
					(ir.getXMax() - ir.getXMin()) / x_factor, (yMax - yMin) / y_factor, SupportedLinearMeasure.CM);

			BufferedImage tempImage = bi.getSubimage(ir.getXMin(), ir.getYMin(), (ir.getXMax() - ir.getXMin()),
					(ir.getYMax() - ir.getYMin()));

			File f = File.createTempFile("temp", ".png");

			System.out.println(f.getAbsolutePath());

			ImageIO.write(tempImage, "png", f);

			Image image = Image.newImage(para1, f.toURI());
			image.setRectangle(fr);

			FrameStyleHandler handler = image.getStyleHandler();
			handler.setHorizontalRelative(HorizontalRelative.PAGE);
			handler.setVerticalRelative(VerticalRelative.PAGE);
			handler.setHorizontalPosition(FrameHorizontalPosition.FROMLEFT);
			handler.setVerticalPosition(FrameVerticalPosition.FROMTOP);

		}

		List<TextRegion> textRegions = page.getTextRegion();
		for (TextRegion textRegion : textRegions) {
			List<Line> textLines = textRegion.getLines();
			for (Line textLine : textLines) {
				FrameRectangle rectangle = getTextRectangle(textLine.getCoords());

				Textbox tBox = para1.addTextbox();

				tBox.setRectangle(rectangle);

				tBox.getStyleHandler().setHorizontalRelative(HorizontalRelative.PAGE);
				tBox.getStyleHandler().setVerticalRelative(VerticalRelative.PAGE);

				Paragraph tempPara = tBox.addParagraph(textLine.getText());
				Font f = tempPara.getFont();
				f.setSize((textLine.getAHeight() / y_factor) / point_to_centimeter);
				tempPara.setFont(f);

				System.out.println(textLine.getText() + " " + rectangle.getXDesc() + " " + rectangle.getYDesc());
			}

		}

		File odtFile = File.createTempFile("OCTOutput", ".odt");
		outputOdt.save(odtFile.getAbsolutePath());
		return odtFile.getAbsolutePath();

	}

	private void calculateReduceY(Page page) {

		int y_Min = Integer.MAX_VALUE;
		for (TextRegion textR : page.getTextRegion()) {
			if (y_Min > textR.getBaseline() - textR.getAHeight())
				y_Min = textR.getBaseline() - textR.getAHeight();
		}

		for (ImageRegion image : page.getImageRegion()) {
			if (y_Min > image.getYMin())
				y_Min = image.getYMin();
		}

		System.out.println("Reduce Y = " + y_Min);
		reduce_y = y_Min;

	}

	private FrameRectangle getTextRectangle(Coords coords) {

		int xMin = Integer.MAX_VALUE, xMax = Integer.MIN_VALUE, yMin = Integer.MAX_VALUE, yMax = Integer.MIN_VALUE;

		for (in.rkvsraman.ocr.olena.scribocli.PcGts.Page.TextRegion.Line.Coords.Point p : coords.getPoint()) {
			if (p.getX() < xMin) {
				xMin = p.getX();
			}
			if (p.getX() > xMax) {
				xMax = p.getX();
			}
			if (p.getY() < yMin) {
				yMin = p.getY();
			}
			if (p.getY() > yMax) {
				yMax = p.getY();
			}

		}

		yMin -= reduce_y;
		yMax -= reduce_y;
		System.out.println(xMin + " " + yMin + " " + (xMax - xMin) + " " + (yMax - yMin));
		System.out.println(xMin / x_factor + " " + yMin / y_factor + " " + (xMax - xMin) / x_factor + " "
				+ (yMax - yMin) / y_factor);
		return new FrameRectangle(xMin / x_factor, yMin / y_factor, (xMax - xMin) / x_factor, (yMax - yMin) / y_factor,
				SupportedLinearMeasure.CM);

	}

}
