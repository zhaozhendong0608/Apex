package com.apex.network.engine;

import com.apex.network.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网络拓扑仿真算法引擎单元测试 (JUnit 5)
 * 对标 TEST-NET-01 测试说明书用例矩阵 (TC-NET-01 ~ TC-NET-05)
 */
class BfsRoutingEngineTest {

    private BfsRoutingEngine routingEngine;
    private TopoGraph demoGraph;

    @BeforeEach
    void setUp() {
        AclRuleMatcher matcher = new AclRuleMatcher();
        routingEngine = new BfsRoutingEngine(matcher);

        // 构造典型工业电力仿真拓扑:
        // PC (IT Zone) -> IT_SW (IT Zone) -> FW (DMZ) -> TDengine (OT Zone)
        TopoNode pc = TopoNode.builder()
                .id("node_pc")
                .name("研发办公PC")
                .type("HOST")
                .zone("IT_ZONE")
                .ip("192.168.1.10")
                .build();

        TopoNode sw = TopoNode.builder()
                .id("node_sw")
                .name("IT交换机")
                .type("SWITCH")
                .zone("IT_ZONE")
                .ip("192.168.1.254")
                .build();

        // 防火墙节点，配置两条 ACL：
        // 1. 放行 TDengine 6030 端口
        // 2. 阻断 SSH 22 端口
        AclRule rulePermit6030 = AclRule.builder()
                .id("acl_1")
                .priority(1)
                .action(AclAction.PERMIT)
                .destPort(6030)
                .protocol("TCP")
                .description("放行时序库 6030 端口")
                .build();

        AclRule ruleDeny22 = AclRule.builder()
                .id("acl_2")
                .priority(2)
                .action(AclAction.DENY)
                .destPort(22)
                .protocol("TCP")
                .description("禁止通过 SSH 22 端口登录")
                .build();

        TopoNode fw = TopoNode.builder()
                .id("node_fw")
                .name("工业核心防火墙")
                .type("FIREWALL")
                .zone("DMZ_ZONE")
                .ip("192.168.1.1")
                .acls(List.of(rulePermit6030, ruleDeny22))
                .build();

        TopoNode tdengine = TopoNode.builder()
                .id("node_tdengine")
                .name("TDengine时序库")
                .type("TDENGINE")
                .zone("OT_ZONE")
                .ip("10.1.1.5")
                .build();

        // 隔离节点 (无物理链路相连)
        TopoNode isolatedServer = TopoNode.builder()
                .id("node_isolated")
                .name("隔离备用机")
                .type("SERVER")
                .zone("OT_ZONE")
                .ip("10.1.1.99")
                .build();

        // 连线
        TopoEdge e1 = TopoEdge.builder().id("e1").sourceNodeId("node_pc").targetNodeId("node_sw").build();
        TopoEdge e2 = TopoEdge.builder().id("e2").sourceNodeId("node_sw").targetNodeId("node_fw").build();
        TopoEdge e3 = TopoEdge.builder().id("e3").sourceNodeId("node_fw").targetNodeId("node_tdengine").build();

        demoGraph = TopoGraph.builder()
                .id(1L)
                .name("电力边缘采集仿真拓扑")
                .nodes(List.of(pc, sw, fw, tdengine, isolatedServer))
                .edges(List.of(e1, e2, e3))
                .build();
    }

    @Test
    @DisplayName("TC-NET-01: 命中 PERMIT 规则，PC 成功连通 TDengine 6030 端口")
    void testPermitRuleConnectivity() {
        SimResultVO result = routingEngine.simulate(demoGraph, "node_pc", "node_tdengine", 6030, "TCP");

        assertTrue(result.getReachable(), "应该判定网络可达");
        assertFalse(result.getBlocked(), "不应该被拦截");
        assertEquals(3, result.getHops(), "跳数应为 3 (PC -> SW -> FW -> TDengine)");
        assertEquals(List.of("node_pc", "node_sw", "node_fw", "node_tdengine"), result.getNodePath());
        System.out.println("✅ TC-NET-01 测试通过: 6030 端口放行成功！");
    }

    @Test
    @DisplayName("TC-NET-02: 命中 DENY 规则，PC 访问 SSH 22 端口被防火墙拦截阻断")
    void testDenyRuleBlockedByFirewall() {
        SimResultVO result = routingEngine.simulate(demoGraph, "node_pc", "node_tdengine", 22, "TCP");

        assertFalse(result.getReachable(), "应该判定网络不可达");
        assertTrue(result.getBlocked(), "应该被防火墙阻断");
        assertEquals("node_fw", result.getBlockedNodeId(), "阻断节点应为防火墙");
        assertEquals("acl_2", result.getBlockedRuleId(), "拦截规则 ID 应为 acl_2");
        assertEquals(List.of("node_pc", "node_sw", "node_fw"), result.getNodePath());
        System.out.println("✅ TC-NET-02 测试通过: 22 端口被防火墙安全策略精准拦截！");
    }

    @Test
    @DisplayName("TC-NET-04: 默认放行 (Default Permit)，未命中规则的端口允许通行")
    void testDefaultPermitPolicy() {
        SimResultVO result = routingEngine.simulate(demoGraph, "node_pc", "node_tdengine", 8080, "TCP");

        assertTrue(result.getReachable(), "默认放行模式下应该判定网络可达");
        assertFalse(result.getBlocked());
        System.out.println("✅ TC-NET-04 测试通过: 默认放行策略生效！");
    }

    @Test
    @DisplayName("TC-NET-05: 物理链路不通，访问隔离备用机判定不可达")
    void testPhysicalUnreachable() {
        SimResultVO result = routingEngine.simulate(demoGraph, "node_pc", "node_isolated", 80, "TCP");

        assertFalse(result.getReachable(), "无物理连线应判定不可达");
        assertFalse(result.getBlocked(), "并非被 ACL 阻断");
        System.out.println("✅ TC-NET-05 测试通过: 无物理连线正确识别！");
    }

    @Test
    @DisplayName("TC-NET-06: 电力二次系统安全网闸(GAP)硬件反向阻断：IT向OT反向穿透被强行切断")
    void testGapHardwareReverseIsolation() {
        // 构造包含网闸的独立拓扑：PC (IT) -> GAP (DMZ) -> PLC (OT)
        TopoNode pc = TopoNode.builder().id("pc_it").name("办公PC").type("HOST").zone("IT_ZONE").ip("192.168.1.10").build();
        TopoNode gap = TopoNode.builder().id("gap_dmz").name("单向安全隔离网闸").type("GAP").zone("DMZ_ZONE").ip("10.0.0.2").build();
        TopoNode plc = TopoNode.builder().id("plc_ot").name("产线PLC").type("PLC").zone("OT_ZONE").ip("172.16.1.50").build();

        TopoEdge e1 = TopoEdge.builder().id("ge1").sourceNodeId("pc_it").targetNodeId("gap_dmz").build();
        TopoEdge e2 = TopoEdge.builder().id("ge2").sourceNodeId("gap_dmz").targetNodeId("plc_ot").build();

        TopoGraph gapGraph = TopoGraph.builder()
                .id(2L).name("网闸安防拓扑")
                .nodes(List.of(pc, gap, plc))
                .edges(List.of(e1, e2))
                .build();

        // 1. 测试反向穿透：IT -> OT (应被网闸硬件阻断)
        SimResultVO reverseRes = routingEngine.simulate(gapGraph, "pc_it", "plc_ot", 80, "TCP");
        assertFalse(reverseRes.getReachable(), "IT 向 OT 反向穿透必须不可达");
        assertTrue(reverseRes.getBlocked(), "必须被网闸物理阻断");
        assertEquals("gap_dmz", reverseRes.getBlockedNodeId());
        assertEquals("GAP_HARDWARE_REVERSE_ISOLATION", reverseRes.getBlockedRuleId());
        System.out.println("✅ TC-NET-06 测试通过: 网闸成功阻断 IT ➔ OT 反向穿透请求！");

        // 2. 测试正向采集摆渡：OT -> IT (允许单向采集透传)
        SimResultVO forwardRes = routingEngine.simulate(gapGraph, "plc_ot", "pc_it", 6030, "TCP");
        assertTrue(forwardRes.getReachable(), "OT 向 IT 正向采集摆渡允许通行");
        assertFalse(forwardRes.getBlocked(), "不应被拦截");
        System.out.println("✅ TC-NET-07 测试通过: 网闸成功放行 OT ➔ IT 正向指标回传！");
    }

    @Test
    @DisplayName("TC-NET-08: 工业物理链路故障切断与备用冗余路径自愈 (Fault Injection & Redundancy Rerouting)")
    void testLinkFailureAndRedundancyRerouting() {
        // 构造双路由冗余网络拓扑：
        // 主路径: PC -> SW_MAIN -> TDengine
        // 备用路径: PC -> SW_BACKUP -> TDengine
        TopoNode pc = TopoNode.builder().id("pc_node").name("工控机").type("HOST").zone("IT_ZONE").ip("192.168.1.10").build();
        TopoNode swMain = TopoNode.builder().id("sw_main").name("主交换机").type("SWITCH").zone("IT_ZONE").ip("192.168.1.1").build();
        TopoNode swBackup = TopoNode.builder().id("sw_backup").name("冗余备用交换机").type("SWITCH").zone("IT_ZONE").ip("192.168.1.2").build();
        TopoNode tdengine = TopoNode.builder().id("db_node").name("时序库").type("TDENGINE").zone("OT_ZONE").ip("10.1.1.5").build();

        // 主路径连线 (主链路注入故障 disabled = true)
        TopoEdge eMain1 = TopoEdge.builder().id("em1").sourceNodeId("pc_node").targetNodeId("sw_main").disabled(true).build();
        TopoEdge eMain2 = TopoEdge.builder().id("em2").sourceNodeId("sw_main").targetNodeId("db_node").build();

        // 备用路径连线 (正常通畅 disabled = false)
        TopoEdge eBkp1 = TopoEdge.builder().id("eb1").sourceNodeId("pc_node").targetNodeId("sw_backup").disabled(false).build();
        TopoEdge eBkp2 = TopoEdge.builder().id("eb2").sourceNodeId("sw_backup").targetNodeId("db_node").disabled(false).build();

        TopoGraph redundancyGraph = TopoGraph.builder()
                .id(3L).name("双网冗余拓扑")
                .nodes(List.of(pc, swMain, swBackup, tdengine))
                .edges(List.of(eMain1, eMain2, eBkp1, eBkp2))
                .build();

        SimResultVO result = routingEngine.simulate(redundancyGraph, "pc_node", "db_node", 6030, "TCP");

        assertTrue(result.getReachable(), "主链路故障时应自动由备用链路冗余自愈");
        assertEquals(List.of("pc_node", "sw_backup", "db_node"), result.getNodePath(), "应绕开故障的主交换机链路");
        System.out.println("✅ TC-NET-08 测试通过: 故障注入后成功通过冗余备用网络自愈！");
    }

    @Test
    @DisplayName("TC-NET-09: 中继设备硬件宕机/断电注入，自动避开宕机设备并自愈")
    void testNodeCrashAndRerouting() {
        // 同样拓扑，链路都正常，但主交换机 sw_main 宕机 (disabled = true)
        TopoNode pc = TopoNode.builder().id("pc_node").name("工控机").type("HOST").zone("IT_ZONE").ip("192.168.1.10").build();
        TopoNode swMain = TopoNode.builder().id("sw_main").name("主交换机").type("SWITCH").zone("IT_ZONE").ip("192.168.1.1").disabled(true).build();
        TopoNode swBackup = TopoNode.builder().id("sw_backup").name("冗余备用交换机").type("SWITCH").zone("IT_ZONE").ip("192.168.1.2").disabled(false).build();
        TopoNode tdengine = TopoNode.builder().id("db_node").name("时序库").type("TDENGINE").zone("OT_ZONE").ip("10.1.1.5").build();

        TopoEdge eMain1 = TopoEdge.builder().id("em1").sourceNodeId("pc_node").targetNodeId("sw_main").build();
        TopoEdge eMain2 = TopoEdge.builder().id("em2").sourceNodeId("sw_main").targetNodeId("db_node").build();
        TopoEdge eBkp1 = TopoEdge.builder().id("eb1").sourceNodeId("pc_node").targetNodeId("sw_backup").build();
        TopoEdge eBkp2 = TopoEdge.builder().id("eb2").sourceNodeId("sw_backup").targetNodeId("db_node").build();

        TopoGraph crashGraph = TopoGraph.builder()
                .id(4L).name("设备宕机拓扑")
                .nodes(List.of(pc, swMain, swBackup, tdengine))
                .edges(List.of(eMain1, eMain2, eBkp1, eBkp2))
                .build();

        SimResultVO result = routingEngine.simulate(crashGraph, "pc_node", "db_node", 6030, "TCP");

        assertTrue(result.getReachable(), "主设备宕机时应自动通过备用交换机寻路");
        assertEquals(List.of("pc_node", "sw_backup", "db_node"), result.getNodePath());
        System.out.println("✅ TC-NET-09 测试通过: 硬件宕机后成功绕障并实现冗余连通！");
    }

    @Test
    @DisplayName("TC-NET-10: 静态路由(LPM)引导转发，严禁向无关终端(监控大屏)与旁路网闸广播漫游")
    void testStaticRouteAccurateForwarding() {
        // 构造 Demo 相似拓扑：
        // PC (192.168.1.10) -> Router (192.168.1.1)
        // Router 连接 Grafana (192.168.1.5)、GAP (10.0.0.2)、FW (10.0.0.1)
        // FW 连接 OT_SW (172.16.1.1) -> TDengine (172.16.1.100)
        // Router 配置静态路由：172.16.1.0/24 -> 10.0.0.1
        TopoNode pc = TopoNode.builder().id("pc").name("工程师站PC").type("HOST").zone("IT_ZONE").ip("192.168.1.10").gateway("192.168.1.1").build();
        TopoNode grafana = TopoNode.builder().id("grafana").name("监控大屏").type("SERVER").zone("IT_ZONE").ip("192.168.1.5").build();
        
        RouteEntry routeToOt = RouteEntry.builder()
                .id("r1").destSubnet("172.16.1.0").mask("255.255.255.0").nextHopIp("10.0.0.1").description("前往OT核心")
                .build();
        TopoNode router = TopoNode.builder().id("router").name("IT核心路由器").type("ROUTER").zone("IT_ZONE").ip("192.168.1.1")
                .routes(List.of(routeToOt)).build();

        TopoNode gap = TopoNode.builder().id("gap").name("单向安全网闸").type("GAP").zone("DMZ_ZONE").ip("10.0.0.2").build();
        TopoNode fw = TopoNode.builder().id("fw").name("边界防火墙").type("FIREWALL").zone("DMZ_ZONE").ip("10.0.0.1").build();
        TopoNode otSw = TopoNode.builder().id("otsw").name("工业交换机").type("SWITCH").zone("OT_ZONE").ip("172.16.1.1").build();
        TopoNode tdengine = TopoNode.builder().id("tdengine").name("时序数据库").type("TDENGINE").zone("OT_ZONE").ip("172.16.1.100").build();

        List<TopoEdge> edges = List.of(
                TopoEdge.builder().id("e1").sourceNodeId("pc").targetNodeId("router").build(),
                TopoEdge.builder().id("e2").sourceNodeId("grafana").targetNodeId("router").build(),
                TopoEdge.builder().id("e3").sourceNodeId("router").targetNodeId("gap").build(),
                TopoEdge.builder().id("e4").sourceNodeId("router").targetNodeId("fw").build(),
                TopoEdge.builder().id("e5").sourceNodeId("fw").targetNodeId("otsw").build(),
                TopoEdge.builder().id("e6").sourceNodeId("gap").targetNodeId("otsw").build(),
                TopoEdge.builder().id("e7").sourceNodeId("otsw").targetNodeId("tdengine").build()
        );

        TopoGraph fullGraph = TopoGraph.builder()
                .id(5L).name("企业级完整互联拓扑")
                .nodes(List.of(pc, grafana, router, gap, fw, otSw, tdengine))
                .edges(edges)
                .build();

        SimResultVO result = routingEngine.simulate(fullGraph, "pc", "tdengine", 6030, "TCP");

        assertTrue(result.getReachable(), "静态路由引导下应顺利跨区抵达时序数据库");
        assertFalse(result.getBlocked(), "不应触发网闸误阻断");
        assertEquals(List.of("pc", "router", "fw", "otsw", "tdengine"), result.getNodePath(), "路径应严格沿着静态路由下一跳 FW 递送，绝不触碰 grafana 或 gap");
        System.out.println("✅ TC-NET-10 测试通过: 静态路由(LPM)精准引导下一跳，成功抵御无效广播漫游！");
    }

    @Test
    @DisplayName("TC-NET-11: SNAT 源地址伪装出境仿真与日志验证")
    void testSnatSourceAddressTranslation() {
        TopoNode pc = TopoNode.builder().id("pc").name("工程师站PC").type("HOST").zone("IT_ZONE").ip("192.168.1.10").build();
        NatRule snatRule = NatRule.builder()
                .id("snat_1")
                .type("SNAT")
                .origSrc("192.168.1.0/24")
                .transSrc("10.0.0.1")
                .description("IT私网出境源地址伪装")
                .build();
        TopoNode fw = TopoNode.builder().id("fw").name("边界防火墙").type("FIREWALL").zone("DMZ_ZONE").ip("10.0.0.1")
                .natRules(List.of(snatRule))
                .build();
        TopoNode server = TopoNode.builder().id("server").name("DMZ核心业务服务").type("SERVER").zone("DMZ_ZONE").ip("10.0.0.5").build();

        TopoGraph graph = TopoGraph.builder()
                .id(6L).name("SNAT测试拓扑")
                .nodes(List.of(pc, fw, server))
                .edges(List.of(
                        TopoEdge.builder().id("e1").sourceNodeId("pc").targetNodeId("fw").build(),
                        TopoEdge.builder().id("e2").sourceNodeId("fw").targetNodeId("server").build()
                ))
                .build();

        SimResultVO result = routingEngine.simulate(graph, "pc", "server", 80, "TCP");

        assertTrue(result.getReachable(), "SNAT 转换后应顺利抵达目的服务");
        boolean hasNatLog = result.getLogs().stream().anyMatch(l -> l.contains("[NAT TRANSLATION]") && l.contains("SNAT"));
        assertTrue(hasNatLog, "仿真日志中必须包含 [NAT TRANSLATION] 出境 SNAT 记录");
        System.out.println("✅ TC-NET-11 测试通过: SNAT 源地址伪装出境仿真与日志验证成功！");
    }

    @Test
    @DisplayName("TC-NET-12: DNAT 目的地址与端口映射入站仿真验证")
    void testDnatDestinationAddressTranslation() {
        TopoNode client = TopoNode.builder().id("client").name("外部访问终端").type("HOST").zone("IT_ZONE").ip("192.168.1.20").build();
        NatRule dnatRule = NatRule.builder()
                .id("dnat_1")
                .type("DNAT")
                .extIp("10.0.0.1")
                .extPort(8080)
                .intIp("172.16.1.100")
                .intPort(6030)
                .protocol("TCP")
                .description("对外8080端口映射至内部时序数据库6030")
                .build();
        // 防火墙放行转换后的 6030 端口
        AclRule permitAcl = AclRule.builder()
                .id("acl_dnat_permit")
                .priority(1)
                .action(AclAction.PERMIT)
                .destPort(6030)
                .protocol("TCP")
                .description("放行DNAT后的目标端口6030")
                .build();
        TopoNode fw = TopoNode.builder().id("fw").name("边界防火墙").type("FIREWALL").zone("DMZ_ZONE").ip("10.0.0.1")
                .natRules(List.of(dnatRule))
                .acls(List.of(permitAcl))
                .build();
        TopoNode db = TopoNode.builder().id("db").name("时序数据库").type("TDENGINE").zone("OT_ZONE").ip("172.16.1.100").build();

        TopoGraph graph = TopoGraph.builder()
                .id(7L).name("DNAT测试拓扑")
                .nodes(List.of(client, fw, db))
                .edges(List.of(
                        TopoEdge.builder().id("e1").sourceNodeId("client").targetNodeId("fw").build(),
                        TopoEdge.builder().id("e2").sourceNodeId("fw").targetNodeId("db").build()
                ))
                .build();

        // 外部请求发送至 10.0.0.1:8080
        SimResultVO result = routingEngine.simulate(graph, "client", "db", 8080, "TCP");

        assertTrue(result.getReachable(), "经过 DNAT 目的端口重写与安全策略放行后，应顺利连通");
        boolean hasNatLog = result.getLogs().stream().anyMatch(l -> l.contains("[NAT TRANSLATION]") && l.contains("DNAT") && l.contains("6030"));
        assertTrue(hasNatLog, "仿真日志中必须包含 [NAT TRANSLATION] 入站 DNAT 记录并显示重写后端口 6030");
        System.out.println("✅ TC-NET-12 测试通过: DNAT 目的端口重写与安全放行入站验证成功！");
    }
}
