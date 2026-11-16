package utils;

import java.awt.Font;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.List;
import model.Order;
import model.OrderDetail;

public final class ReceiptPrinter {
    private ReceiptPrinter() {}
    public static void print(Order order, List<OrderDetail> details) throws PrinterException {
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
        if(job.printDialog())job.print();
    }
}
