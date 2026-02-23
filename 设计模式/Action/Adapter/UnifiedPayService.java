package Action.Adapter;

// 1. 目标接口：电商系统统一支付接口
interface UnifiedPayService {
    void unifiedPay(String orderId, double amount);
}

// 2. 适配者：微信支付第三方接口（接口不兼容）
class WechatThirdPartyPay {
    public void wechatPay(String outTradeNo, String totalFee) {
        System.out.println("微信第三方支付：订单[" + outTradeNo + "]，金额：" + totalFee);
    }
}

// 2. 适配者：支付宝第三方接口（接口不兼容）
class AlipayThirdPartyPay {
    public void alipayPay(String tradeNo, String amount) {
        System.out.println("支付宝第三方支付：订单[" + tradeNo + "]，金额：" + amount);
    }
}

// 3. 适配器：微信支付适配器
class WechatPayAdapter implements UnifiedPayService {
    private WechatThirdPartyPay wechatPay = new WechatThirdPartyPay();

    @Override
    public void unifiedPay(String orderId, double amount) {
        // 适配：参数转换、接口调用
        wechatPay.wechatPay(orderId, String.valueOf(amount));
    }
}

// 3. 适配器：支付宝支付适配器
class AlipayPayAdapter implements UnifiedPayService {
    private AlipayThirdPartyPay alipayPay = new AlipayThirdPartyPay();

    @Override
    public void unifiedPay(String orderId, double amount) {
        // 适配：参数转换、接口调用
        alipayPay.alipayPay(orderId, String.valueOf(amount));
    }
}

// 测试类
class AdapterTest {
    public static void main(String[] args) {
        // 统一接口调用，无需关心第三方接口差异
        UnifiedPayService wechat = new WechatPayAdapter();
        wechat.unifiedPay("ORD001", 99.9);

        UnifiedPayService alipay = new AlipayPayAdapter();
        alipay.unifiedPay("ORD002", 199.9);
    }
}