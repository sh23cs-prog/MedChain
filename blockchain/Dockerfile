# MedChain Hardhat Node — Docker Image
FROM node:18-alpine

WORKDIR /app

# Copy package files
COPY package.json package-lock.json* ./

# Install dependencies
RUN npm install

# Copy contracts and config
COPY hardhat.config.js ./
COPY contracts/ ./contracts/
COPY scripts/ ./scripts/

# Expose Hardhat JSON-RPC port
EXPOSE 8545

# Start Hardhat node and deploy contract
CMD ["sh", "-c", "npx hardhat node --hostname 0.0.0.0 & sleep 5 && npx hardhat run scripts/deploy.js --network localhost && wait"]
