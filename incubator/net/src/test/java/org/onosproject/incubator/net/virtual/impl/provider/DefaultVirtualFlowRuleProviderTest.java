/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.incubator.net.virtual.impl.provider;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onlab.osgi.ServiceDirectory;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.core.IdGenerator;
import org.onosproject.core.Version;
import org.onosproject.incubator.net.virtual.DefaultVirtualDevice;
import org.onosproject.incubator.net.virtual.DefaultVirtualNetwork;
import org.onosproject.incubator.net.virtual.DefaultVirtualPort;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.incubator.net.virtual.TenantId;
import org.onosproject.incubator.net.virtual.VirtualDevice;
import org.onosproject.incubator.net.virtual.VirtualHost;
import org.onosproject.incubator.net.virtual.VirtualLink;
import org.onosproject.incubator.net.virtual.VirtualNetwork;
import org.onosproject.incubator.net.virtual.VirtualNetworkAdminService;
import org.onosproject.incubator.net.virtual.VirtualPort;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.DefaultDevice;
import org.onosproject.net.DefaultLink;
import org.onosproject.net.DefaultPath;
import org.onosproject.net.DefaultPort;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.DisjointPath;
import org.onosproject.net.HostId;
import org.onosproject.net.HostLocation;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceServiceAdapter;
import org.onosproject.net.flow.DefaultFlowEntry;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleListener;
import org.onosproject.net.flow.FlowRuleOperations;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TableStatisticsEntry;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.L2ModificationInstruction;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.topology.ClusterId;
import org.onosproject.net.topology.LinkWeight;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyCluster;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class DefaultVirtualFlowRuleProviderTest {
    private static final ProviderId PID = new ProviderId("of", "foo");

    private static final DeviceId DID1 = DeviceId.deviceId("of:001");
    private static final DeviceId DID2 = DeviceId.deviceId("of:002");
    private static final PortNumber PORT_NUM1 = PortNumber.portNumber(1);
    private static final PortNumber PORT_NUM2 = PortNumber.portNumber(2);

    private static final DefaultAnnotations ANNOTATIONS =
            DefaultAnnotations.builder().set("foo", "bar").build();

    private static final Device DEV1 =
            new DefaultDevice(PID, DID1, Device.Type.SWITCH, "", "", "", "", null);
    private static final Device DEV2 =
            new DefaultDevice(PID, DID2, Device.Type.SWITCH, "", "", "", "", null);
    private static final Port PORT11 =
            new DefaultPort(DEV1, PORT_NUM1, true, ANNOTATIONS);
    private static final Port PORT12 =
            new DefaultPort(DEV1, PORT_NUM2, true, ANNOTATIONS);
    private static final Port PORT21 =
            new DefaultPort(DEV2, PORT_NUM1, true, ANNOTATIONS);
    private static final Port PORT22 =
            new DefaultPort(DEV2, PORT_NUM2, true, ANNOTATIONS);

    private static final ConnectPoint CP11 = new ConnectPoint(DID1, PORT_NUM1);
    private static final ConnectPoint CP12 = new ConnectPoint(DID1, PORT_NUM2);
    private static final ConnectPoint CP21 = new ConnectPoint(DID2, PORT_NUM1);
    private static final ConnectPoint CP22 = new ConnectPoint(DID2, PORT_NUM2);
    private static final Link LINK1 = DefaultLink.builder()
            .src(CP12).dst(CP21).providerId(PID).type(Link.Type.DIRECT).build();

    private static final NetworkId VNET_ID = NetworkId.networkId(1);
    private static final DeviceId VDID = DeviceId.deviceId("of:100");

    private static final VirtualNetwork VNET = new DefaultVirtualNetwork(
            VNET_ID, TenantId.tenantId("t1"));
    private static final VirtualDevice VDEV =
            new DefaultVirtualDevice(VNET_ID, VDID);
    private static final VirtualPort VPORT1 =
            new DefaultVirtualPort(VNET_ID, VDEV, PORT_NUM1, CP11);
    private static final VirtualPort VPORT2 =
            new DefaultVirtualPort(VNET_ID, VDEV, PORT_NUM2, CP22);

    private static final int TIMEOUT = 10;


    protected DefaultVirtualFlowRuleProvider virtualProvider;

    private ApplicationId vAppId;

    @Before
    public void setUp() {
        virtualProvider = new DefaultVirtualFlowRuleProvider();

        virtualProvider.deviceService = new TestDeviceService();
        virtualProvider.coreService = new TestCoreService();
        virtualProvider.virtualNetworkAdminService =
                new TestVirtualNetworkAdminService();
        virtualProvider.topologyService = new TestTopologyService();
        virtualProvider.flowRuleService = new TestFlowRuleService();
        virtualProvider.providerRegistryService = new VirtualProviderManager();

        virtualProvider.activate();
        vAppId = new TestApplicationId(0, "Virtual App");
    }

    @After
    public void tearDown() {
        virtualProvider.deactivate();
        virtualProvider.deviceService = null;
        virtualProvider.coreService = null;
    }

    @Test
    public void virtualizeFlowRuleWithInPort() {
        TrafficSelector ts = DefaultTrafficSelector.builder()
                .matchInPort(PORT_NUM1).build();
        TrafficTreatment tr = DefaultTrafficTreatment.builder()
                .setOutput(PORT_NUM2).build();

        FlowRule r1 = DefaultFlowRule.builder()
                .forDevice(VDID)
                .withSelector(ts)
                .withTreatment(tr)
                .withPriority(10)
                .fromApp(vAppId)
                .makeTemporary(TIMEOUT)
                .build();

        virtualProvider.applyFlowRule(VNET_ID, r1);

        assertEquals("2 rules should exist", 2,
                     virtualProvider.flowRuleService.getFlowRuleCount());

        Set<FlowEntry> phyRules = new HashSet<>();
        for (FlowEntry i : virtualProvider.flowRuleService.getFlowEntries(DID1)) {
            phyRules.add(i);
        }
        for (FlowEntry i : virtualProvider.flowRuleService.getFlowEntries(DID2)) {
            phyRules.add(i);
        }

        FlowRule in = null;
        FlowRule out = null;

        for (FlowRule rule : phyRules) {

            L2ModificationInstruction i = (L2ModificationInstruction)
                    rule.treatment().allInstructions().get(0);

            if (i.subtype() == L2ModificationInstruction.L2SubType.VLAN_PUSH) {
                in = rule;
            } else {
                out = rule;
            }

        }

        assertEquals(DID1, in.deviceId());
        assertEquals(DID2, out.deviceId());
    }

    @Test
    public void virtualizeFlowRuleWithoutInPort() {
        TrafficSelector ts = DefaultTrafficSelector.builder().build();
        TrafficTreatment tr = DefaultTrafficTreatment.builder()
                .setOutput(PORT_NUM2).build();

        FlowRule r1 = DefaultFlowRule.builder()
                .forDevice(VDID)
                .withSelector(ts)
                .withTreatment(tr)
                .withPriority(10)
                .fromApp(vAppId)
                .makeTemporary(TIMEOUT)
                .build();

        virtualProvider.applyFlowRule(VNET_ID, r1);

        assertEquals("3 rules should exist", 3,
                     virtualProvider.flowRuleService.getFlowRuleCount());

        FlowRule inFromDID1 = null;
        FlowRule inFromDID2 = null;
        FlowRule out = null;

        Set<FlowEntry> phyRules = new HashSet<>();
        for (FlowEntry i : virtualProvider.flowRuleService.getFlowEntries(DID1)) {
            phyRules.add(i);
        }
        for (FlowEntry i : virtualProvider.flowRuleService.getFlowEntries(DID2)) {
            phyRules.add(i);
        }

        for (FlowRule rule : phyRules) {
            for (Instruction inst : rule.treatment().allInstructions()) {
                if (inst.type() == Instruction.Type.L2MODIFICATION) {
                    L2ModificationInstruction i = (L2ModificationInstruction) inst;
                    if (i.subtype() == L2ModificationInstruction.L2SubType.VLAN_PUSH) {
                        inFromDID1 = rule;
                        break;
                    } else {
                        out = rule;
                        break;
                    }
                } else {
                    inFromDID2 = rule;
                    break;
                }
            }
        }

        assertEquals(DID1, inFromDID1.deviceId());
        assertEquals(DID2, inFromDID2.deviceId());
        assertEquals(DID2, out.deviceId());
    }

    @Test
    public void removeVirtualizeFlowRule() {
        TrafficSelector ts = DefaultTrafficSelector.builder().build();
        TrafficTreatment tr = DefaultTrafficTreatment.builder()
                .setOutput(PORT_NUM2).build();

        FlowRule r1 = DefaultFlowRule.builder()
                .forDevice(VDID)
                .withSelector(ts)
                .withTreatment(tr)
                .withPriority(10)
                .fromApp(vAppId)
                .makeTemporary(TIMEOUT)
                .build();

        virtualProvider.removeFlowRule(VNET_ID, r1);

        assertEquals("0 rules should exist", 0,
                     virtualProvider.flowRuleService.getFlowRuleCount());
    }


    private static class TestDeviceService extends DeviceServiceAdapter {
        @Override
        public int getDeviceCount() {
            return 2;
        }

        @Override
        public Iterable<Device> getDevices() {
            return ImmutableList.of(DEV1, DEV2);
        }

        @Override
        public Iterable<Device> getAvailableDevices() {
            return getDevices();
        }

        @Override
        public Device getDevice(DeviceId deviceId) {
            return deviceId.equals(DID2) ? DEV2 : DEV1;
        }
    }

    private static class TestCoreService implements CoreService {

        @Override
        public Version version() {
            return null;
        }

        @Override
        public Set<ApplicationId> getAppIds() {
            return null;
        }

        @Override
        public ApplicationId getAppId(Short id) {
            return null;
        }

        @Override
        public ApplicationId getAppId(String name) {
            return null;
        }

        @Override
        public ApplicationId registerApplication(String name) {
            return new TestApplicationId(1, name);
        }

        @Override
        public ApplicationId registerApplication(String name,
                                                 Runnable preDeactivate) {
            return null;
        }

        @Override
        public IdGenerator getIdGenerator(String topic) {
            return null;
        }
    }

    private static class TestApplicationId extends DefaultApplicationId {
        public TestApplicationId(int id, String name) {
            super(id, name);
        }
    }

    private static class TestVirtualNetworkAdminService
            implements VirtualNetworkAdminService {

        @Override
        public Set<VirtualNetwork> getVirtualNetworks(TenantId tenantId) {
            return null;
        }

        @Override
        public Set<VirtualDevice> getVirtualDevices(NetworkId networkId) {
            return ImmutableSet.of(VDEV);
        }

        @Override
        public Set<VirtualHost> getVirtualHosts(NetworkId networkId) {
            return null;
        }

        @Override
        public Set<VirtualLink> getVirtualLinks(NetworkId networkId) {
            return null;
        }

        @Override
        public Set<VirtualPort> getVirtualPorts(NetworkId networkId,
                                                DeviceId deviceId) {
            return ImmutableSet.of(VPORT1, VPORT2);
        }

        @Override
        public <T> T get(NetworkId networkId, Class<T> serviceClass) {
            return null;
        }

        @Override
        public ServiceDirectory getServiceDirectory() {
            return null;
        }

        @Override
        public void registerTenantId(TenantId tenantId) {

        }

        @Override
        public void unregisterTenantId(TenantId tenantId) {

        }

        @Override
        public Set<TenantId> getTenantIds() {
            return null;
        }

        @Override
        public VirtualNetwork createVirtualNetwork(TenantId tenantId) {
            return null;
        }

        @Override
        public void removeVirtualNetwork(NetworkId networkId) {

        }

        @Override
        public VirtualDevice createVirtualDevice(NetworkId networkId,
                                                 DeviceId deviceId) {
            return null;
        }

        @Override
        public void removeVirtualDevice(NetworkId networkId, DeviceId deviceId) {

        }

        @Override
        public VirtualHost createVirtualHost(NetworkId networkId, HostId hostId,
                                             MacAddress mac, VlanId vlan,
                                             HostLocation location,
                                             Set<IpAddress> ips) {
            return null;
        }

        @Override
        public void removeVirtualHost(NetworkId networkId, HostId hostId) {

        }

        @Override
        public VirtualLink createVirtualLink(NetworkId networkId,
                                             ConnectPoint src, ConnectPoint dst) {
            return null;
        }

        @Override
        public void removeVirtualLink(NetworkId networkId,
                                      ConnectPoint src, ConnectPoint dst) {

        }

        @Override
        public VirtualPort createVirtualPort(NetworkId networkId,
                                             DeviceId deviceId,
                                             PortNumber portNumber,
                                             ConnectPoint realizedBy) {
            return null;
        }

        @Override
        public void bindVirtualPort(NetworkId networkId,
                                    DeviceId deviceId,
                                    PortNumber portNumber,
                                    ConnectPoint realizedBy) {

        }

        @Override
        public void removeVirtualPort(NetworkId networkId, DeviceId deviceId,
                                      PortNumber portNumber) {

        }
    }

    private static class TestTopologyService implements TopologyService {

        @Override
        public void addListener(TopologyListener listener) {

        }

        @Override
        public void removeListener(TopologyListener listener) {

        }

        @Override
        public Topology currentTopology() {
            return null;
        }

        @Override
        public boolean isLatest(Topology topology) {
            return false;
        }

        @Override
        public TopologyGraph getGraph(Topology topology) {
            return null;
        }

        @Override
        public Set<TopologyCluster> getClusters(Topology topology) {
            return null;
        }

        @Override
        public TopologyCluster getCluster(Topology topology, ClusterId clusterId) {
            return null;
        }

        @Override
        public Set<DeviceId> getClusterDevices(Topology topology,
                                               TopologyCluster cluster) {
            return null;
        }

        @Override
        public Set<Link> getClusterLinks(Topology topology,
                                         TopologyCluster cluster) {
            return null;
        }

        @Override
        public Set<Path> getPaths(Topology topology, DeviceId src, DeviceId dst) {
            DefaultPath path = new DefaultPath(PID, ImmutableList.of(LINK1),
                                               100, ANNOTATIONS);
            return ImmutableSet.of(path);
        }

        @Override
        public Set<Path> getPaths(Topology topology, DeviceId src,
                                  DeviceId dst, LinkWeight weight) {
            DefaultPath path = new DefaultPath(PID, ImmutableList.of(LINK1),
                                               100, ANNOTATIONS);
            return ImmutableSet.of(path);
        }

        @Override
        public Set<DisjointPath> getDisjointPaths(Topology topology,
                                                  DeviceId src, DeviceId dst) {
            return null;
        }

        @Override
        public Set<DisjointPath> getDisjointPaths(Topology topology, DeviceId src,
                                                  DeviceId dst, LinkWeight weight) {
            return null;
        }

        @Override
        public Set<DisjointPath> getDisjointPaths(Topology topology, DeviceId src,
                                                  DeviceId dst,
                                                  Map<Link, Object> riskProfile) {
            return null;
        }

        @Override
        public Set<DisjointPath> getDisjointPaths(Topology topology, DeviceId src,
                                                  DeviceId dst, LinkWeight weight,
                                                  Map<Link, Object> riskProfile) {
            return null;
        }

        @Override
        public boolean isInfrastructure(Topology topology,
                                        ConnectPoint connectPoint) {
            return false;
        }

        @Override
        public boolean isBroadcastPoint(Topology topology,
                                        ConnectPoint connectPoint) {
            return false;
        }
    }

    private static class TestFlowRuleService implements FlowRuleService {
        static Set<FlowRule> ruleCollection = new HashSet<>();

        @Override
        public void addListener(FlowRuleListener listener) {

        }

        @Override
        public void removeListener(FlowRuleListener listener) {

        }

        @Override
        public int getFlowRuleCount() {
            return ruleCollection.size();
        }

        @Override
        public Iterable<FlowEntry> getFlowEntries(DeviceId deviceId) {
            return ruleCollection.stream()
                    .filter(r -> r.deviceId().equals(deviceId))
                    .map(r -> new DefaultFlowEntry(r))
                    .collect(Collectors.toSet());
        }

        @Override
        public void applyFlowRules(FlowRule... flowRules) {
            for (FlowRule rule : flowRules) {
                ruleCollection.add(rule);
            }
        }

        @Override
        public void purgeFlowRules(DeviceId deviceId) {

        }

        @Override
        public void removeFlowRules(FlowRule... flowRules) {
            Set<FlowRule> candidates = new HashSet<>();
            for (FlowRule rule : flowRules) {
                ruleCollection.stream()
                        .filter(r -> r.exactMatch(rule))
                        .forEach(candidates::add);
            }
            ruleCollection.removeAll(candidates);
        }

        @Override
        public void removeFlowRulesById(ApplicationId appId) {

        }

        @Override
        public Iterable<FlowRule> getFlowRulesById(ApplicationId id) {
            return null;
        }

        @Override
        public Iterable<FlowEntry> getFlowEntriesById(ApplicationId id) {
            return null;
        }

        @Override
        public Iterable<FlowRule> getFlowRulesByGroupId(ApplicationId appId,
                                                        short groupId) {
            return null;
        }

        @Override
        public void apply(FlowRuleOperations ops) {

        }

        @Override
        public Iterable<TableStatisticsEntry>
        getFlowTableStatistics(DeviceId deviceId) {
            return null;
        }
    }
}
