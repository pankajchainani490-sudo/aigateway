import com.gcd.coding.gcdgatewaycore.filter.risk.RiskControlFilter;
import jakarta.annotation.Resource;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestRisk {
    @Resource
    private RiskControlFilter riskControlFilter;

    @Test
    public void testRisk() {
    }
}
