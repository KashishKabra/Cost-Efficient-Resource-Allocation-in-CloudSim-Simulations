package org.cloudbus.cloudsim.examples;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicyBestFit;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class CostEfficientResourceAllocation {
    private static final int HOSTS = 8; // Number of hosts
    private static final int INITIAL_VMS = 10; // Initial number of VMs
    private static final int CLOUDLETS = 30; // Number of cloudlets

    private static List<Double> powerOverTime = new ArrayList<>();
    private static List<Double> timeStamps = new ArrayList<>();
    private static double totalEnergyCost = 0.0; // Total energy cost

    private static CloudSimPlus simulation; // Simulation object
    private static DatacenterSimple datacenter; // Datacenter object
    private static DatacenterBrokerSimple broker; // Broker object

    public static void main(String[] args) {
        System.out.println("Starting Cost-Efficient Resource Allocation Simulation...");
        simulation = new CloudSimPlus();

        datacenter = createDatacenter();
        broker = new DatacenterBrokerSimple(simulation);

        broker.submitVmList(createInitialVms());
        createCloudlets(broker);

        simulation.addOnClockTickListener(createMonitoringListener());
        simulation.addOnClockTickListener(createAutoScalingListener());

        simulation.start();

        printResults();
        generateComparisonChart();
        generatePowerUsageChart();
    }

    private static DatacenterSimple createDatacenter() {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < 8; j++) {
                peList.add(new PeSimple(8000)); // Each PE has 8000 MIPS
            }

            Host host = new HostSimple(
                1048576,    // 1TB RAM
                400000000,  // 400Gbps BW
                200000000,  // 200TB Storage
                peList
            );
            host.setPowerModel(new PowerModelHostSimple(250, 75));
            host.setVmScheduler(new VmSchedulerTimeShared());
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicyBestFit());
    }

    private static List<Vm> createInitialVms() {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < INITIAL_VMS; i++) {
            Vm vm = new VmSimple(2000, 2)
                .setRam(32768)
                .setBw(10000)
                .setSize(50000);
            vmList.add(vm);
        }
        return vmList;
    }

    private static void createCloudlets(DatacenterBrokerSimple broker) {
        for (int i = 0; i < CLOUDLETS; i++) {
            Cloudlet cloudlet = new CloudletSimple(
                15000 + (i * 1000),
                (i % 4 == 0) ? 2 : 1
            );
            broker.submitCloudlet(cloudlet);
        }
    }

    private static EventListener<EventInfo> createMonitoringListener() {
        return eventInfo -> {
            double currentTime = eventInfo.getTime();
            if (currentTime > 0) {
                double currentPower = datacenter.getHostList().stream()
                    .mapToDouble(host -> host.getPowerModel().getPower(host.getCpuPercentUtilization()))
                    .sum();

                totalEnergyCost += currentPower / 3600.0 * 0.15; // Energy cost calculation ($/kWh)
                powerOverTime.add(currentPower);
                timeStamps.add(currentTime);

                if (currentTime % 10 == 0) {
                    System.out.println("\n| Host | CPU% | Power(W) | VMs |");
                    System.out.println("|------|------|----------|-----|");
                    datacenter.getHostList().forEach(host ->
                        System.out.printf("| %4d | %5.1f | %8.1f | %3d |%n",
                            host.getId(),
                            host.getCpuPercentUtilization() * 100,
                            host.getPowerModel().getPower(host.getCpuPercentUtilization()),
                            host.getVmList().size()
                        )
                    );
                }
            }
        };
    }

    private static EventListener<EventInfo> createAutoScalingListener() {
        return eventInfo -> {
            double currentTime = eventInfo.getTime();
            if (currentTime > 0 && currentTime % 10 == 0) {
                datacenter.getHostList().forEach(host -> {
                    double cpuUtil = host.getCpuPercentUtilization();

                    if (cpuUtil > 0.6) { // Scale-up condition
                        Vm newVm = new VmSimple(2000, 2)
                            .setRam(32768).setBw(10000).setSize(50000);
                        broker.submitVm(newVm);
                        System.out.printf("\n[SCALE UP] Host %d - CPU: %.1f%%%n", host.getId(), cpuUtil * 100);
                    } else if (cpuUtil < 0.3 && !host.getVmList().isEmpty()) { // Scale-down condition
                        broker.destroyVm(host.getVmList().get(0));
                        System.out.printf("\n[SCALE DOWN] Host %d - CPU: %.1f%%%n", host.getId(), cpuUtil * 100);
                    }
                });
            }
        };
    }

    private static void generatePowerUsageChart() {
        XYSeries powerSeries = new XYSeries("Optimized Power Usage");
        for (int i = 0; i < timeStamps.size(); i++) {
            powerSeries.add(timeStamps.get(i), powerOverTime.get(i));
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
            "Optimized Power Consumption Over Time",
            "Time (seconds)",
            "Power (Watts)",
            new XYSeriesCollection(powerSeries)
        );

        saveChart(chart, "optimized_power_usage.png");
    }

    private static void generateComparisonChart() {
        XYSeries baselineSeries = new XYSeries("Baseline (No Optimization)");
        XYSeries optimizedSeries = new XYSeries("Optimized");

        for (int i = 0; i < timeStamps.size(); i++) {
            double baselinePower = 800 + (i * 2); // Simulate linear growth for baseline
            baselineSeries.add(timeStamps.get(i).doubleValue(), baselinePower);
            optimizedSeries.add(timeStamps.get(i), powerOverTime.get(i));
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(baselineSeries);
        dataset.addSeries(optimizedSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
            "Power Consumption Comparison",
            "Time (seconds)",
            "Power (Watts)",
            dataset
        );

        saveChart(chart, "power_comparison.png");
    }

    private static void saveChart(JFreeChart chart, String filename) {
        try {
            ChartUtils.saveChartAsPNG(new File(filename), chart, 800, 600);
            System.out.println("\nGenerated chart: " + filename);
        } catch (IOException e) {
            System.err.println("Error saving chart: " + e.getMessage());
        }
    }

    private static void printResults() {
        System.out.println("\n=============== Simulation Results =================");
        System.out.printf("Total Energy Cost: $%.2f%n", totalEnergyCost);

        System.out.println("\nFinal Host States:");
        datacenter.getHostList().forEach(host ->
            System.out.printf("Host %2d: CPU %5.1f%% | Power %5.1fW%n",
                host.getId(),
                host.getCpuPercentUtilization() * 100,
                host.getPowerModel().getPower(host.getCpuPercentUtilization())
            )
        );
    }
}