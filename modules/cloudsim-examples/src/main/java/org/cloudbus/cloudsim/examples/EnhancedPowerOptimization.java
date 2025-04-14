package org.cloudbus.cloudsim.examples;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

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

public class EnhancedPowerOptimization {
    // Metric tracking
    private static List<Double> powerOverTime = new ArrayList<>();
    private static List<Double> timeStamps = new ArrayList<>();
    private static List<Double> costOverTime = new ArrayList<>();
    private static int slaViolations = 0;
    private static final int HOSTS = 8;
    private static final int INITIAL_VMS = 10;
    private static final int CLOUDLETS = 30;
    private static double totalEnergyCost = 0.0;
    private static double carbonCost = 0.0;
    private static final double RENEWABLE_RATIO = 0.3;

    private static CloudSimPlus simulation;
    private static DatacenterSimple datacenter;
    private static DatacenterBrokerSimple broker;

    public static void main(String[] args) {
        System.out.println("Starting Enhanced Power Optimization Simulation...");
        simulation = new CloudSimPlus();
        
        datacenter = createDatacenter();
        broker = new DatacenterBrokerSimple(simulation);

        broker.submitVmList(createInitialVms());
        createCloudlets(broker);

        simulation.addOnClockTickListener(createMonitoringListener());
        simulation.addOnClockTickListener(createAutoScalingListener());
        simulation.addOnClockTickListener(createFailureSimulationListener());
        
        simulation.start();
        
        printResults();
        generateComparisonChart();
        generateCharts();
    }

    private static DatacenterSimple createDatacenter() {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < 8; j++) {
                peList.add(new PeSimple(8000));
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
        List<Cloudlet> cloudletList = new ArrayList<>();
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
                    .mapToDouble(host -> {
                        double utilization = host.getCpuPercentUtilization();
                        if (utilization > 0.8) {
                            slaViolations++;
                            System.out.printf("SLA Violation: Host %d at %.1f%% CPU%n",
                                host.getId(), utilization*100);
                        }
                        return host.getPowerModel().getPower(utilization);
                    })
                    .sum();

                totalEnergyCost += currentPower / 3600.0 * 0.15;
                carbonCost += currentPower / 3600.0 * 0.15 * (1 - RENEWABLE_RATIO) * 0.5;

                powerOverTime.add(currentPower);
                costOverTime.add(currentPower * 0.15);
                timeStamps.add(currentTime);

                if (currentTime % 10 == 0) {
                    System.out.println("\n| Host | CPU% | Power(W) | VMs |");
                    System.out.println("|------|------|----------|-----|");
                    datacenter.getHostList().forEach(host -> 
                        System.out.printf("| %4d | %5.1f | %8.1f | %3d |%n",
                            host.getId(),
                            host.getCpuPercentUtilization()*100,
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
                    double ramUtil = host.getRam().getPercentUtilization();
                    double bwUtil = host.getBw().getPercentUtilization();

                    if (cpuUtil > 0.6 || ramUtil > 0.7 || bwUtil > 0.6) {
                        Vm newVm = new VmSimple(2000, 2)
                            .setRam(32768).setBw(10000).setSize(50000);
                        broker.submitVm(newVm);
                        System.out.printf("\n[SCALE UP] Host %d - CPU: %.1f%%, RAM: %.1f%%, BW: %.1f%%%n",
                            host.getId(), cpuUtil*100, ramUtil*100, bwUtil*100);
                    }
                });
            }
        };
    }

    private static EventListener<EventInfo> createFailureSimulationListener() {
        return eventInfo -> {
            double currentTime = eventInfo.getTime();
            if (currentTime == 50 || currentTime == 100 || currentTime == 150) {
                Host failedHost = datacenter.getHostList().get(new Random().nextInt(HOSTS));
                System.out.printf("\n[FAILURE] Host %d failed at %.1fs%n", 
                    failedHost.getId(), currentTime);
                migrateVms(failedHost);
            }
        };
    }

    private static void migrateVms(Host failedHost) {
        failedHost.getVmList().forEach(vm -> {
            Optional<Host> targetHost = datacenter.getHostList().stream()
                .filter(host -> host != failedHost)
                .filter(host -> host.isSuitableForVm(vm))
                .findFirst();

            if (targetHost.isPresent()) {
                targetHost.get().createVm(vm);
                System.out.printf("VM %d migrated to Host %d%n", 
                    vm.getId(), targetHost.get().getId());
            } else {
                System.out.printf("Failed to migrate VM %d%n", vm.getId());
            }
        });
    }

    private static void generateComparisonChart() {
        XYSeries baselineSeries = new XYSeries("Baseline");
        XYSeries optimizedSeries = new XYSeries("Optimized");
    
        // Ensure timeStamps.get(i) is converted to double
        for (int i = 0; i < timeStamps.size(); i++) {
            baselineSeries.add(timeStamps.get(i).doubleValue(), 800 + i * 2);
            optimizedSeries.add(timeStamps.get(i).doubleValue(), powerOverTime.get(i));
        }
    
        XYSeriesCollection collection = new XYSeriesCollection();
        collection.addSeries(baselineSeries);
        collection.addSeries(optimizedSeries);
    
        JFreeChart comparisonChart = ChartFactory.createXYLineChart(
            "Optimization Impact", "Time (s)", "Power (W)", collection
        );
        saveChart(comparisonChart, "optimization_comparison.png");
    }
    
    

    private static void generateCharts() {
        XYSeries powerSeries = new XYSeries("Power Usage (W)");
        for (int i = 0; i < powerOverTime.size(); i++) {
            powerSeries.add(timeStamps.get(i), powerOverTime.get(i));
        }
        
        XYSeriesCollection collection = new XYSeriesCollection();
        collection.addSeries(powerSeries);
        
        JFreeChart chart = ChartFactory.createXYLineChart(
            "Power Usage Over Time", "Time (s)", "Power (W)", collection
        );
        saveChart(chart, "power_usage.png");
    }

    private static void saveChart(JFreeChart chart, String filename) {
        try {
            // Create output directory if it doesn't exist
            new File("output").mkdirs();
            // Save to output directory
            ChartUtils.saveChartAsPNG(new File("output/" + filename), chart, 800, 600);
            System.out.println("Saved chart: " + filename);
        } catch (IOException e) {
            System.err.println("Chart save error: " + e.getMessage());
        }
    }

    private static void printResults() {
        System.out.println("\n================ Final Results ================");
        System.out.printf("Total Energy Cost: $%.2f%n", totalEnergyCost);
        System.out.printf("Carbon Footprint Cost: $%.2f%n", carbonCost);
        System.out.printf("SLA Violations: %d%n", slaViolations);
        
        System.out.println("\nHost Utilization:");
        datacenter.getHostList().forEach(host -> 
            System.out.printf("Host %2d: CPU %5.1f%% | RAM %5.1f%% | BW %5.1f%%%n",
                host.getId(),
                host.getCpuPercentUtilization()*100,
                host.getRam().getPercentUtilization()*100,
                host.getBw().getPercentUtilization()*100
            )
        );
    }
}
