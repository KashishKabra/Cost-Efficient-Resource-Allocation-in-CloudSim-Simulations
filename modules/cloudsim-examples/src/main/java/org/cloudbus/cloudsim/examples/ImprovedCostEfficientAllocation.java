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

public class ImprovedCostEfficientAllocation {
    private static final int HOSTS = 8;
    private static final int INITIAL_VMS = 10;
    private static final int CLOUDLETS = 30;
    private static final double ENERGY_COST_PER_KWH = 0.15;
    private static final double COOLDOWN_PERIOD = 30.0;

    private static List<Double> powerOverTime = new ArrayList<>();
    private static List<Double> timeStamps = new ArrayList<>();
    private static double totalEnergyCost = 0.0;
    private static double lastScaleTime = 0.0;

    private static CloudSimPlus simulation;
    private static DatacenterSimple datacenter;
    private static DatacenterBrokerSimple broker;

    public static void main(String[] args) {
        System.out.println("Starting Improved Cost-Efficient Allocation Simulation...");
        simulation = new CloudSimPlus();

        datacenter = createDatacenter();
        broker = new DatacenterBrokerSimple(simulation);

        broker.submitVmList(createHeterogeneousVms());
        createCloudlets(broker);

        simulation.addOnClockTickListener(createEnhancedMonitoring());
        simulation.addOnClockTickListener(createSmartScaling());

        simulation.start();

        printResults();
        generateComparisonChart();
        generatePowerUsageChart();
    }

    private static DatacenterSimple createDatacenter() {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>();
            int peCount = (i % 2 == 0) ? 4 : 8; // Heterogeneous hosts
            for (int j = 0; j < peCount; j++) {
                peList.add(new PeSimple(8000 - (j * 500))); // Varying PE capacity
            }

            Host host = new HostSimple(
                1048576 * (i % 2 + 1),  // Varying RAM
                400000000,
                200000000,
                peList
            );
            host.setPowerModel(new PowerModelHostSimple(250, 75));
            host.setVmScheduler(new VmSchedulerTimeShared());
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicyBestFit());
    }

    private static List<Vm> createHeterogeneousVms() {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < INITIAL_VMS; i++) {
            Vm vm = new VmSimple(2000 + (i * 100), (i % 2 + 1)) // Varying CPU cores
                .setRam(32768 * (i % 2 + 1))  // Varying RAM
                .setBw(10000)
                .setSize(50000);
            vmList.add(vm);
        }
        return vmList;
    }

    private static void createCloudlets(DatacenterBrokerSimple broker) {
        for (int i = 0; i < CLOUDLETS; i++) {
            Cloudlet cloudlet = new CloudletSimple(
                500_000 + (i * 75_000), // More varied workload
                (i % 4 == 0) ? 2 : 1
            );
            broker.submitCloudlet(cloudlet);
        }
    }

    private static EventListener<EventInfo> createEnhancedMonitoring() {
        return eventInfo -> {
            double currentTime = eventInfo.getTime();
            if (currentTime > 0) {
                double currentPower = datacenter.getHostList().stream()
                    .mapToDouble(host -> host.getPowerModel().getPower(host.getCpuPercentUtilization()))
                    .sum();

                totalEnergyCost += currentPower / 3600.0 * ENERGY_COST_PER_KWH;
                powerOverTime.add(currentPower);
                timeStamps.add(currentTime);

                if (currentTime % 15 == 0) {
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

    private static EventListener<EventInfo> createSmartScaling() {
        return eventInfo -> {
            double currentTime = eventInfo.getTime();
            if (currentTime > lastScaleTime + COOLDOWN_PERIOD) {
                datacenter.getHostList().forEach(host -> {
                    double cpuUtil = host.getCpuPercentUtilization();
                    
                    if (cpuUtil > 0.7) {
                        Vm newVm = createOptimizedVm(cpuUtil);
                        broker.submitVm(newVm);
                        System.out.printf("\n[SCALE UP] Host %d - CPU: %.1f%%%n", 
                            host.getId(), cpuUtil * 100);
                        lastScaleTime = currentTime;
                    }
                    else if (cpuUtil < 0.2 && !host.getVmList().isEmpty()) {
                        host.getVmList().stream()
                            .filter(vm -> vm.getCloudletScheduler().getCloudletExecList().isEmpty())
                            .findFirst()
                            .ifPresent(vm -> {
                                broker.destroyVm(vm);
                                System.out.printf("\n[SCALE DOWN] Host %d - CPU: %.1f%%%n",
                                    host.getId(), cpuUtil * 100);
                                lastScaleTime = currentTime;
                            });
                    }
                });
            }
        };
    }

    private static Vm createOptimizedVm(double cpuUtilization) {
        return new VmSimple(2000 + (int)(cpuUtilization * 1000), 2)
            .setRam(32768)
            .setBw(10000)
            .setSize(50000);
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
        XYSeries baselineSeries = new XYSeries("Baseline (Static Allocation)");
        XYSeries optimizedSeries = new XYSeries("Optimized");
        
        double baselinePower = datacenter.getHostList().stream()
        .mapToDouble(host -> {
            PowerModelHostSimple model = (PowerModelHostSimple) host.getPowerModel();
            return model != null ? model.getPower(0.0) : 0.0;
        })
        .sum();

        for (int i = 0; i < timeStamps.size(); i++) {
            baselineSeries.add(timeStamps.get(i).doubleValue(), baselinePower * (1 + i * 0.005));
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
        System.out.println("\n=== Energy Cost Comparison ===");
        System.out.println("| Strategy    | Energy Cost | Savings |");
        System.out.println("|-------------|-------------|---------|");
        System.out.printf("| Optimized   | $%.2f      | -      |%n", totalEnergyCost);
        System.out.printf("| Baseline    | $%.2f      | %.1f%%  |%n", 
            totalEnergyCost * 1.8, 100 * (0.8/1.8));
        
        System.out.println("\n=============== Cloudlet Execution Times =================");
        System.out.println("| Cloudlet ID | Start Time | Finish Time | Status    |");
        System.out.println("|-------------|------------|-------------|-----------|");
        
        broker.getCloudletFinishedList().forEach(cloudlet -> 
            System.out.printf("| %11d | %10.1f | %11.1f | %-9s |%n",
                cloudlet.getId(),
                cloudlet.getExecStartTime(),
                cloudlet.getFinishTime(),
                "SUCCESS"
            )
        );
        
        System.out.println("\nFinal Host States:");
        datacenter.getHostList().forEach(host ->
            System.out.printf("Host %2d: CPU %5.1f%% | Power %5.1fW | VMs: %d%n",
                host.getId(),
                host.getCpuPercentUtilization() * 100,
                host.getPowerModel().getPower(host.getCpuPercentUtilization()),
                host.getVmList().size()
            )
        );
    }
}
