# Cost-Efficient-Resource-Allocation-in-CloudSim-Simulations

## 🧩 Overview
This project simulates an energy-efficient virtual machine (VM) allocation strategy in cloud data centers using **CloudSim Plus**. It compares a **static VM allocation approach** with an **optimized dynamic scaling strategy**, showing substantial power and cost savings.

---

## 🚀 Key Features

- **Heterogeneous Environment**  
  Simulates 8 hosts with varied configurations:
  - CPU cores: 4 or 8 PEs
  - RAM: mixed sizes for realism

- **Dynamic VM Scaling**  
  Implements intelligent scaling based on CPU utilization:
  - Scale up when utilization > 70%
  - Scale down when utilization < 20%
  - 30-second cooldown between actions

- **Power Monitoring**  
  Real-time power tracking and energy cost calculation  
  💡 Rate: `$0.15/kWh`

- **Visualization**  
  Auto-generates charts showing power consumption trends  
  - Comparison between static and dynamic strategies

- **CI/CD Integration**  
  Includes a **GitHub Actions** pipeline for automated simulations

---

## 🔧 Implementation Highlights

- `createHeterogeneousVms()`:  
  Generates 10 VMs with varied specs:
  - CPU: 2000–2900 MIPS
  - Cores: Alternates between 1 and 2
  - RAM: Alternates between 32GB and 64GB
  - Bandwidth: 10,000  
  - Storage: 50,000

- Cloudlets:  
  30 tasks submitted with progressively increasing workloads

- Allocation Strategies:
  - **Baseline**: Static Best-Fit (one-time placement)
  - **Optimized**: Dynamic reallocation based on host utilization

---

## 📊 Results

- ⚡ Power consumption reduced from ~1150W to ~600W
- 💰 ~44.4% energy cost savings
- ✅ Maintains workload performance with better efficiency

---

## 🛠️ Technologies Used

- [CloudSim Plus](https://github.com/manoelcampos/cloudsim-plus) – Cloud simulation
- Java 21 – Language and runtime
- JFreeChart – Visualization library
- GitHub Actions – CI/CD automation

---

## 📂 Structure

```bash
.
├── src/
│   └── main/
│       └── java/
│           └── ... (Simulation logic)
├── charts/
│   └── power_consumption_comparison.png
├── .github/
│   └── workflows/
│       └── simulate.yml
└── README.md
