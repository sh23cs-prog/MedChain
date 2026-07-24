const { ethers } = require("hardhat");

async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("Deploying MedChain contract with:", deployer.address);

  const MedChain = await ethers.getContractFactory("MedChain");
  const contract = await MedChain.deploy();
  await contract.waitForDeployment();

  const address = await contract.getAddress();
  console.log("MedChain deployed to:", address);

  // Save address for backend
  const fs = require("fs");
  fs.writeFileSync("./deployed.json", JSON.stringify({ address, network: "localhost" }, null, 2));
  console.log("Contract address saved to deployed.json");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
