package utils;

import java.awt.Font;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.SwingWorker;
import model.Order;
import model.OrderDetail;

public final class ReceiptPrinter {
    private ReceiptPrinter() {}

    /** Loads invoice data and performs printer I/O without blocking the Swing event thread. */
    public static void printAsync(Order order, Callable<List<OrderDetail>> detailsLoader,
            Consumer<Exception> onError, Runnable onFinished) {
        new SwingWorker<PrinterJob, Void>() {
            @Override
            protected PrinterJob doInBackground() throws Exception {
                List<OrderDetail> details = detailsLoader.call();
                return createJob(order, details);
            }

            @Override
            protected void done() {
                final PrinterJob job;
                try {
                    job = get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    finish(exception);
                    return;
                } catch (ExecutionException exception) {
                    finish(asException(exception.getCause()));
                    return;
                }

                // Keep the native printer selection dialog on Swing's event thread.
                try {
                    if (!job.printDialog()) {
                        finish(null);
                        return;
                    }
                } catch (Exception exception) {
                    finish(exception);
                    return;
                }

                new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        job.print();
                        return null;
                    }

                    @Override
                    protected void done() {
                        Exception failure = null;
                        try {
                            get();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            failure = exception;
                        } catch (ExecutionException exception) {
                            failure = asException(exception.getCause());
                        }
                        finish(failure);
                    }
                }.execute();
            }

            private void finish(Exception exception) {
                try {
                    if (exception != null && onError != null) onError.accept(exception);
                } finally {
                    if (onFinished != null) onFinished.run();
                }
            }
        }.execute();
    }

    public static void print(Order order, List<OrderDetail> details) throws PrinterException {
        PrinterJob job = createJob(order, details);
        if (job.printDialog()) job.print();
    }

    private static PrinterJob createJob(Order order, List<OrderDetail> details) {
        StringBuilder text=new StringBuilder("          STORE DRINK\n              HÓA ĐƠN\n--------------------------------\n")
                .append("Mã đơn: #").append(order.getId()).append('\n')
                .append("Khách hàng: ").append(order.getCustomerName()==null?"Khách lẻ":order.getCustomerName()).append('\n');
        if(order.getTableId()!=null)text.append("Bàn: ").append(String.format("%02d",order.getTableId())).append('\n');
        text.append("Thanh toán: ").append("CASH".equalsIgnoreCase(order.getPaymentMethod())?"Tiền mặt":"Chuyển khoản").append("\n--------------------------------\n");
        for(OrderDetail d:details){
            text.append(d.getDrinkName()==null?"Món #"+d.getDrinkId():d.getDrinkName()).append(" - Size ").append(d.getSize()==null?"S":d.getSize()).append('\n')
                    .append("  ").append(d.getQuantity()).append(" x ").append(String.format("%,.0f",d.getPrice())).append(" = ").append(String.format("%,.0f",d.getSubtotal())).append(" đ\n");
            if(d.getToppings()!=null)for(var t:d.getToppings())text.append("  + ").append(t.name).append(" (").append(String.format("%,.0f",t.price)).append(" đ)\n");
            if(d.getNote()!=null&&!d.getNote().isBlank())text.append("  Ghi chú: ").append(d.getNote()).append('\n');
        }
        text.append("--------------------------------\nTỔNG THANH TOÁN: ").append(String.format("%,.0f",order.getTotal())).append(" đ\nXin cảm ơn quý khách!\n");
        PrinterJob job=PrinterJob.getPrinterJob();job.setPrintable((graphics,pageFormat,pageIndex)->{if(pageIndex>0)return java.awt.print.Printable.NO_SUCH_PAGE;graphics.setFont(new Font(Font.MONOSPACED,Font.PLAIN,10));int y=35;for(String line:text.toString().split("\\R")){graphics.drawString(line,30,y);y+=15;}return java.awt.print.Printable.PAGE_EXISTS;});
        return job;
    }

    private static Exception asException(Throwable failure) {
        return failure instanceof Exception exception ? exception : new Exception(failure);
    }
}
