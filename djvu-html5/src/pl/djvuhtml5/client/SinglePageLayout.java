package pl.djvuhtml5.client;

import static pl.djvuhtml5.client.TileCache.MAX_SUBSAMPLE;
import static pl.djvuhtml5.client.TileCache.toSubsample;
import static pl.djvuhtml5.client.TileCache.toZoom;

import java.util.ArrayList;

import pl.djvuhtml5.client.PageCache.PageDownloadListener;
import pl.djvuhtml5.client.TileCache.TileCacheListener;
import pl.djvuhtml5.client.TileCache.TileInfo;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Image;
import com.lizardtech.djvu.DjVuInfo;
import com.lizardtech.djvu.DjVuPage;
import com.lizardtech.djvu.Document;

public class SinglePageLayout implements PageDownloadListener, TileCacheListener {

	private double zoom = 0;

	private double zoom100;

	private int page = 0;

	private DjVuInfo pageInfo;

	/**
	 * Location of the center of the screen in the coordinates of the scaled
	 * page (one point is one pixel on the screen).
	 */
	private int centerX, centerY;

	final TileCache tileCache;

	private final PageCache pageCache;

	private final Canvas canvas;

	private final Toolbar toolbar;

	private String background;
	
	private final int pageMargin;

	private TileInfo tileInfoTemp = new TileInfo();

	public SinglePageLayout(Canvas canvas, Toolbar toolbar, Document document) {
		this.pageCache = new PageCache(document);
		this.tileCache = new TileCache(pageCache);
		pageCache.addPageDownloadListener(this);
		tileCache.addTileCacheListener(this);
		this.canvas = canvas;
		this.toolbar = toolbar;

		this.background = DjvuContext.getBackground();
		this.pageMargin = DjvuContext.getPageMargin();

		toolbar.setPageCount(pageCache.getPageCount());

		new PanController(canvas);
	}

	public void setPage(int pageNum) {
		page = pageNum;
		DjVuPage newPage = pageCache.getPage(pageNum);
		if (newPage != null) {
			pageInfo = newPage.getInfo();
			toolbar.setZoomOptions(findZoomOptions());
			checkBounds();
			redraw();
		}
	}

	public int getPage() {
		return page;
	}

	public void canvasResized() {
		redraw();
	}

	public void zoomToFitPage() {
		if (pageInfo == null)
			return;
		doSetZoom(Math.min((1.0f * canvas.getCoordinateSpaceWidth() - pageMargin * 2) / pageInfo.width,
				(1.0f * canvas.getCoordinateSpaceHeight() - pageMargin * 2) / pageInfo.height));
	}

	public void zoomToFitWidth() {
		if (pageInfo == null)
			return;
		doSetZoom((1.0f * canvas.getCoordinateSpaceWidth() - pageMargin * 2) / pageInfo.width);
	}

	public void setZoom(int percent) {
		if (pageInfo == null)
			return;
		doSetZoom(percent * zoom100 / 100);
	}

	public int getZoom() {
		return (int) (zoom / zoom100 * 100 + 0.5);
	}

	private void doSetZoom(double zoom) {
		centerX *= zoom / this.zoom;
		centerY *= zoom / this.zoom;
		this.zoom = zoom;
		checkBounds();
		redraw();
	}

	private ArrayList<Integer> findZoomOptions() {
		ArrayList<Integer> result = new ArrayList<>();
		result.add(100);
		final int screenDPI = DjvuContext.getScreenDPI();
		zoom100 = 1.0 * screenDPI / pageInfo.dpi;
		int subsample = toSubsample(zoom100);
		if (toZoom(subsample) / zoom100 > zoom100 / toZoom(subsample + 1))
			subsample++;
		zoom100 = toZoom(subsample);

		if (zoom == 0)
			zoom = zoom100;

		double z = zoom100;
		for (int i = subsample + 1; i <= MAX_SUBSAMPLE; i++) {
			double z2 = toZoom(i);
			if (z / z2 > 1.2) {
				z = z2;
				result.add((int) (z / zoom100 * 100 + 0.5));
			}
		}

		z = zoom100;
		for (int i = subsample - 1; i >= 1; i--) {
			double z2 = toZoom(i);
			if (z2 / z > 1.2) {
				z = z2;
				result.add(0, (int) (z / zoom100 * 100 + 0.5));
			}
		}
		return result;
	}

	private void checkBounds() {
		int w = canvas.getCoordinateSpaceWidth(), h = canvas.getCoordinateSpaceHeight();
		int pw = (int) (pageInfo.width * zoom), ph = (int) (pageInfo.height * zoom);
		if (pw < w) {
			centerX = pw / 2;
		} else {
			centerX = Math.max(centerX, w / 2 - pageMargin);
			centerX = Math.min(centerX, pw - w / 2 + pageMargin);
		}
	
		if (ph < h) {
			centerY = ph / 2;
		} else {
			centerY = Math.max(centerY, h / 2 - pageMargin);
			centerY = Math.min(centerY, ph - h / 2 + pageMargin);
		}
	}

	public void redraw() {
		Context2d graphics2d = canvas.getContext2d();
		int w = canvas.getCoordinateSpaceWidth(), h = canvas.getCoordinateSpaceHeight();
		if (pageInfo == null) {
			graphics2d.setFillStyle(background);
			graphics2d.fillRect(0, 0, w, h);
			return;
		}
		int pw = (int) (pageInfo.width * zoom), ph = (int) (pageInfo.height * zoom);
		if (pw < w || ph < h) {
			graphics2d.setFillStyle(background);
			graphics2d.fillRect(0, 0, w, h);
		}
		int subsample = toSubsample(zoom);
		double scale = zoom / toZoom(subsample);
		graphics2d.save();
		int startX = w / 2 - centerX, startY = h / 2 - centerY;
		graphics2d.translate(startX, startY);
		graphics2d.scale(scale, scale);
		graphics2d.translate(-startX, -startY);

		int tileSize = tileCache.tileSize;
		int fromX = Math.max(0, centerX - w / 2) / tileSize;
		int toX = Math.min((int) Math.ceil(pw / scale), centerX + w / 2) / tileSize;
		int fromY = Math.max(0, centerY - h / 2) / tileSize;
		int toY = Math.min((int) Math.ceil(ph / scale), centerY + h / 2) / tileSize;
		tileInfoTemp.page = page;
		tileInfoTemp.subsample = subsample;
		for (int y = toY; y >= fromY; y--) { //reversed order for nicer effect of cache filling
			for (int  x = toX; x >= fromX; x--) { 
				tileInfoTemp.x = x;
				tileInfoTemp.y = y;
				Image tileImage = tileCache.getTileImage(tileInfoTemp);
				graphics2d.drawImage(ImageElement.as(tileImage.getElement()),
						startX + x * tileSize, startY + y * tileSize);
			}
		}
		graphics2d.restore();
	}

	@Override
	public void pageAvailable(int pageNum) {
		if (pageNum == page) {
			setPage(pageNum);
			redraw();
		}
	}

	@Override
	public void tileAvailable(TileInfo tileInfo) {
		if (tileInfo.page == page)
			redraw();
	}

	private class PanController implements MouseDownHandler, MouseUpHandler, MouseMoveHandler {

		private boolean isDown = false;
		private int x, y;

		public PanController(Canvas canvas) {
			canvas.addMouseDownHandler(this);
			canvas.addMouseUpHandler(this);
			canvas.addMouseMoveHandler(this);
		}

		@Override
		public void onMouseDown(MouseDownEvent event) {
			int button = event.getNativeButton();
			if (button == NativeEvent.BUTTON_LEFT || button == NativeEvent.BUTTON_MIDDLE) {
				isDown = true;
				x = event.getX();
				y = event.getY();
				event.preventDefault();
				Event.setCapture(canvas.getElement());
			}
		}

		@Override
		public void onMouseUp(MouseUpEvent event) {
			isDown = false;
			Event.releaseCapture(canvas.getElement());
		}

		@Override
		public void onMouseMove(MouseMoveEvent event) {
			if (isDown) {
				centerX -= event.getX() - x;
				centerY -= event.getY() - y;
				x = event.getX();
				y = event.getY();
				checkBounds();
				redraw();
			}
		}

	}
}
