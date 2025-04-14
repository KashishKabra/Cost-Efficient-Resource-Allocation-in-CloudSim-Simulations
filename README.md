# Enhanced Power Optimization in CloudSim Simulations

## Overview
This project focuses on simulating and optimizing power consumption in cloud data centers using the CloudSim Plus framework. It incorporates advanced features such as power monitoring, SLA violation tracking, auto-scaling, and failure simulation to evaluate energy efficiency and cost-effectiveness in a cloud computing environment.

## Key Features
- **Power Optimization**: Tracks power usage over time and calculates energy costs.
- **SLA Violation Monitoring**: Detects SLA violations based on CPU utilization thresholds.
- **Auto-Scaling**: Dynamically adjusts virtual machines (VMs) based on resource utilization.
- **Failure Simulation**: Simulates host failures and migrates VMs to maintain service continuity.
- **Visualization**: Generates charts comparing baseline and optimized power usage.

## Simulation Details
The simulation includes:
- 8 hosts with 1TB RAM, 400Gbps bandwidth, and 200TB storage each.
- 10 initial VMs with dynamic scaling based on resource usage.
- 30 cloudlets representing user tasks with varying computational requirements.
- Renewable energy ratio of 30% factored into carbon cost calculations.

## Results
The simulation outputs:
1. Total energy cost and carbon footprint cost.
2. SLA violations during the simulation.
3. Host utilization metrics (CPU, RAM, Bandwidth).
4. Comparison charts for baseline vs. optimized power usage.
 
