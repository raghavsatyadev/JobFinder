package org.cef.browser;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.cef.CefClient;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;

/**
 * Off-screen (windowless) browser with a fixed view rect and no GL canvas.
 *
 * <p>JCEF's own {@code CefBrowserOsr} takes its view rect from a JOGL {@code GLCanvas}. A hidden
 * browser never realizes that canvas, so its view rect stays at the 1x1 placeholder and pages see
 * {@code window.innerWidth == 1}. This browser reports a fixed size instead and drops the painted
 * frames, because scrapers only read the DOM. It must live in {@code org.cef.browser} because
 * {@code CefBrowser_N} is package-private.
 *
 * <p>Create it, then call {@link #createImmediately()}; there is no parent window.
 */
public final class HeadlessOsrBrowser extends CefBrowser_N implements CefRenderHandler {
  private final Rectangle viewRect;
  private volatile boolean focused;

  public HeadlessOsrBrowser(
      CefClient client, String url, CefRequestContext context, int width, int height) {
    super(client, url, context, null, null, null);
    viewRect = new Rectangle(0, 0, width, height);
    // No parent window can veto the close, so let CEF close it whenever it asks.
    setCloseAllowed();
  }

  @Override
  public void createImmediately() {
    createBrowser(getClient(), 0, getUrl(), true, false, null, getRequestContext());
  }

  /**
   * Forwards only focus changes. {@code CefClient.onGotFocus} calls {@code setFocus(true)}, and for
   * a windowless browser CEF answers that with another synchronous {@code OnGotFocus}, which
   * recursed until the EDT overflowed its stack.
   */
  @Override
  public void setFocus(boolean enable) {
    if (focused == enable) return;
    focused = enable;
    super.setFocus(enable);
  }

  @Override
  public Component getUIComponent() {
    return null;
  }

  @Override
  public CefRenderHandler getRenderHandler() {
    return this;
  }

  @Override
  protected CefBrowser_N createDevToolsBrowser(
      CefClient client,
      String url,
      CefRequestContext context,
      CefBrowser_N parent,
      Point inspectAt) {
    throw new UnsupportedOperationException("DevTools are not available in a headless browser");
  }

  @Override
  public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
    CompletableFuture<BufferedImage> result = new CompletableFuture<>();
    result.completeExceptionally(
        new UnsupportedOperationException("A headless browser does not keep painted frames"));
    return result;
  }

  // CefRenderHandler

  @Override
  public Rectangle getViewRect(CefBrowser browser) {
    return new Rectangle(viewRect);
  }

  @Override
  public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
    screenInfo.Set(1.0, 32, 8, false, new Rectangle(viewRect), new Rectangle(viewRect));
    return true;
  }

  @Override
  public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
    return new Point(viewPoint);
  }

  @Override
  public void onPopupShow(CefBrowser browser, boolean show) {}

  @Override
  public void onPopupSize(CefBrowser browser, Rectangle size) {}

  @Override
  public void onPaint(
      CefBrowser browser,
      boolean popup,
      Rectangle[] dirtyRects,
      ByteBuffer buffer,
      int width,
      int height) {}

  @Override
  public void addOnPaintListener(Consumer<CefPaintEvent> listener) {}

  @Override
  public void setOnPaintListener(Consumer<CefPaintEvent> listener) {}

  @Override
  public void removeOnPaintListener(Consumer<CefPaintEvent> listener) {}

  @Override
  public boolean onCursorChange(CefBrowser browser, int cursorType) {
    return true;
  }

  @Override
  public boolean startDragging(
      CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
    return false;
  }

  @Override
  public void updateDragCursor(CefBrowser browser, int operation) {}
}
